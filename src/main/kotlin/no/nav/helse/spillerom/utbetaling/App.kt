package no.nav.helse.spillerom.utbetaling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.DBModule
import no.nav.helse.spillerom.utbetaling.kafka.KafkaConsumerImpl
import org.apache.kafka.common.KafkaException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLTransientException
import java.util.concurrent.Executors

// App-oppstarten må definere egen logger her, siden den (per nå) ikke skjer inne i en klasse
val appLogger: Logger = LoggerFactory.getLogger("spillerom-utbetaling")

fun main() {
    startApp(Configuration.fromEnv())
}

var isReady = false
var isAlive = false

private fun shouldCauseRestart(ex: Throwable): Boolean =
    (ex is KafkaException) || (ex is SQLTransientException) || (ex is Error) // || restartExceptionClassNames.contains(ex.javaClass.canonicalName)

internal fun startApp(configuration: Configuration) {
    appLogger.info("Setter opp data source")
    val dataSource = instansierDatabase(configuration.db)

    // val applicationContext = Dispatchers.IO (?)
    val applicationContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    val exceptionHandler =
        CoroutineExceptionHandler { _, ex ->
            appLogger.error("Uhåndtert feil", ex)
            if (shouldCauseRestart(ex)) {
                appLogger.error("Setting status to UN-healthy")
                isAlive = false
                isReady = false
            }
        }

    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(applicationContext + exceptionHandler) {
        launch {
            embeddedServer(CIO, port = 8080) {
                appLogger.info("Setter opp ktor")
                helsesjekker()
                appLogger.info("Starter spillerom-utbetaling")
            }.start(true)
        }
        launch {
            Appen(dataSource).start()
        }
    }
}

class Appen(private val dataSource: javax.sql.DataSource) {
    private lateinit var kafkaConsumer: KafkaConsumerImpl

    fun start() {
        appLogger.info("Starter selve appen!!")

        // Start Kafka consumer
        kafkaConsumer = KafkaConsumerImpl(dataSource)
        kafkaConsumer.start()
        appLogger.info("Kafka consumer startet")

        isAlive = true
        isReady = true
    }

    fun stop() {
        appLogger.info("Stopper appen")
        kafkaConsumer.stop()
        isAlive = false
        isReady = false
    }
}

internal fun instansierDatabase(configuration: Configuration.DB) = DBModule(configuration = configuration).also { it.migrate() }.dataSource

internal fun Application.helsesjekker() {
    routing {
        get("/isready") {
            if (isReady) {
                call.respondText("READY", ContentType.Text.Plain)
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, "NOT_READY")
            }
        }
        get("/isalive") {
            if (isAlive) {
                call.respondText("ALIVE", ContentType.Text.Plain)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "NOT_ALIVE")
            }
        }
    }
}
