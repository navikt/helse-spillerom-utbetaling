package no.nav.helse.spillerom.utbetaling.infrastruktur.db

import no.nav.helse.spillerom.utbetaling.Configuration

class DBModule(configuration: Configuration.DB) {
    val dataSource = DataSourceBuilder(configuration).build()
    private val flywayMigrator = FlywayMigrator(configuration)

    fun migrate() {
        flywayMigrator.migrate()
    }
}
