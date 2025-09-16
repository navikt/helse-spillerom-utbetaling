package no.nav.helse.bakrommet.infrastruktur.db

import no.nav.helse.bakrommet.Configuration

class DBModule(configuration: Configuration.DB) {
    val dataSource = DataSourceBuilder(configuration).build()
    private val flywayMigrator = FlywayMigrator(configuration)

    fun migrate() {
        flywayMigrator.migrate()
    }
}
