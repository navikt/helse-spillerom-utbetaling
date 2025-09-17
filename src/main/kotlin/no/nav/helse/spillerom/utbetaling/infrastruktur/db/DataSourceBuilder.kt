package no.nav.helse.spillerom.utbetaling.infrastruktur.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.spillerom.utbetaling.Configuration
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DataSourceBuilder(configuration: Configuration.DB) {
    private val hikariConfig =
        HikariConfig().apply {
            jdbcUrl = configuration.jdbcUrl
            maximumPoolSize = 20
            minimumIdle = 2
            idleTimeout = 1.minutes.inWholeMilliseconds
            maxLifetime = idleTimeout * 5
            initializationFailTimeout = 1.minutes.inWholeMilliseconds
            connectionTimeout = 5.seconds.inWholeMilliseconds
            leakDetectionThreshold = 30.seconds.inWholeMilliseconds
        }

    fun build(): DataSource = HikariDataSource(hikariConfig)
}
