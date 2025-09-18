package no.nav.helse.spillerom.utbetaling.kafka

import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import javax.sql.DataSource

class KafkaConsumerImpl(
    private val inboxDao: GodkjenteBehandlingerInboxDao,
    private val topic: String = "speilvendt.spillerom-behandlinger",
) : KafkaConsumerInterface {
    private val logger: Logger = LoggerFactory.getLogger(KafkaConsumerImpl::class.java)
    private val meldingProsesserer = MeldingProsesserer(inboxDao)
    private val consumer: KafkaConsumer<String, String>
    private var isRunning = false
    private var consumerJob: Job? = null

    constructor(dataSource: DataSource, topic: String = "speilvendt.spillerom-behandlinger") : this(
        GodkjenteBehandlingerInboxDao(dataSource),
        topic,
    )

    init {
        val config =
            KafkaUtils.getAivenKafkaConfig("spillerom-utbetaling-consumer")
                .toConsumerConfig(StringDeserializer::class, StringDeserializer::class)
        consumer = KafkaConsumer(config)
    }

    override fun start() {
        if (isRunning) {
            logger.warn("Consumer er allerede startet")
            return
        }

        isRunning = true
        consumer.subscribe(listOf(topic))
        logger.info("Kafka consumer startet og abonnerer på topic: $topic")

        consumerJob =
            CoroutineScope(Dispatchers.IO).launch {
                while (isRunning) {
                    try {
                        val records = consumer.poll(Duration.ofMillis(1000))
                        if (records.isEmpty) {
                            continue
                        }

                        logger.debug("Mottok ${records.count()} meldinger fra Kafka")

                        var alleProsessert = true
                        records.forEach { record ->
                            try {
                                meldingProsesserer.prosesserMelding(record)
                            } catch (e: Exception) {
                                logger.error("Feil ved prosessering av melding med key: ${record.key()}, offset: ${record.offset()}", e)
                                alleProsessert = false
                                // Ikke commit offset hvis en melding feiler - dette sikrer at-least-once delivery
                            }
                        }

                        // Commit offset kun hvis alle meldinger ble prosessert uten feil
                        if (alleProsessert) {
                            consumer.commitSync()
                            logger.debug("Committet offset for ${records.count()} meldinger")
                        } else {
                            logger.warn("Ikke committet offset på grunn av feil i prosessering - meldinger vil bli reprosessert")
                        }
                    } catch (e: Exception) {
                        logger.error("Feil ved polling av Kafka meldinger", e)
                        delay(5000) // Vent 5 sekunder før neste forsøk
                    }
                }
            }
    }

    override fun stop() {
        isRunning = false
        consumerJob?.cancel()
        consumer.close()
        logger.info("Kafka consumer stoppet")
    }
}
