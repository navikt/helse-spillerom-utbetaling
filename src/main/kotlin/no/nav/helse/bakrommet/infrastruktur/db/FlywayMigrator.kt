package no.nav.helse.bakrommet.infrastruktur.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.bakrommet.Configuration
import org.flywaydb.core.Flyway
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FlywayMigrator(configuration: Configuration.DB) {
    private val hikariConfig =
        HikariConfig().apply {
            jdbcUrl = configuration.jdbcUrl
            connectionTimeout = 5.seconds.inWholeMilliseconds
            initializationFailTimeout = 1.minutes.inWholeMilliseconds
            maximumPoolSize = 2
        }

    fun migrate() {
        HikariDataSource(hikariConfig).use { dataSource ->
            Flyway.configure()
                .validateMigrationNaming(true)
                .dataSource(dataSource)
                .lockRetryCount(-1)
                .load()
                .migrate()
        }
    }
}
