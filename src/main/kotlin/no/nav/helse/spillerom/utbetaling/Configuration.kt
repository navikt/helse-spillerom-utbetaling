package no.nav.helse.spillerom.utbetaling

data class Configuration(
    val db: DB,
    val naisClusterName: String,
) {
    data class DB(
        val jdbcUrl: String,
    )

    companion object {
        private fun String.asSet() = this.split(",").map { it.trim() }.toSet()

        fun fromEnv(env: Map<String, String> = System.getenv()): Configuration {
            return Configuration(
                db = DB(jdbcUrl = env.getValue("DATABASE_JDBC_URL")),
                naisClusterName = env.getValue("NAIS_CLUSTER_NAME"),
            )
        }
    }
}
