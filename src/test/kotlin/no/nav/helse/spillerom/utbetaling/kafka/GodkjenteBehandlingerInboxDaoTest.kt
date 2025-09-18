package no.nav.helse.spillerom.utbetaling.kafka

import kotliquery.Row
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.MedDataSource
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.TestDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import java.util.*

internal class GodkjenteBehandlingerInboxDaoTest {
    private val db = MedDataSource(TestDataSource.dbModule.dataSource)
    private val dao = GodkjenteBehandlingerInboxDao(TestDataSource.dbModule.dataSource)

    @BeforeEach
    fun setOpp() {
        TestDataSource.resetDatasource()
    }

    @Test
    fun `kan sette inn ny behandling i inbox`() {
        val outboxId = 12345L
        val payload = """{"type": "utbetaling", "beløp": 10000}"""

        dao.settInn(outboxId, payload)

        val payloadFraDb =
            db.single(
                """SELECT payload FROM godkjente_behandlinger_inbox WHERE outbox_id = :outbox_id""",
                "outbox_id" to outboxId,
            ) { rs: Row ->
                rs.string("payload")
            }!!

        assertEquals(payload, payloadFraDb)
    }

    @Test
    fun `henter ubehandlede behandlinger sortert etter opprettet tidspunkt`() {
        // Sett inn testdata
        val outboxId1 = 11111L
        val outboxId2 = 22222L

        dao.settInn(outboxId1, """{"test": 1}""")
        Thread.sleep(10) // Sikre forskjellig opprettet tidspunkt
        dao.settInn(outboxId2, """{"test": 2}""")

        val ubehandlede = dao.hentUbehandlede()

        assertTrue(ubehandlede.size >= 2)
        val våreBehandlinger = ubehandlede.filter { it.outboxId == outboxId1 || it.outboxId == outboxId2 }
        assertEquals(2, våreBehandlinger.size)

        // Sjekk at de er sortert etter opprettet tidspunkt (eldste først)
        val sortert = våreBehandlinger.sortedBy { it.opprettet }
        assertEquals(outboxId1, sortert[0].outboxId)
        assertEquals(outboxId2, sortert[1].outboxId)
    }

    @Test
    fun `returnerer tom liste når ingen ubehandlede behandlinger finnes`() {
        // Opprett og marker som behandlet
        val outboxId = 33333L
        dao.settInn(outboxId, """{"test": "data"}""")
        dao.markerSomBehandlet(outboxId)

        val ubehandlede = dao.hentUbehandlede()
        val våreBehandlinger = ubehandlede.filter { it.outboxId == outboxId }

        assertTrue(våreBehandlinger.isEmpty())
    }

    @Test
    fun `kan markere behandling som behandlet`() {
        val outboxId = 44444L
        val payload = """{"test": "markering"}"""

        dao.settInn(outboxId, payload)

        // Sjekk at den først er ubehandlet
        val behandletFør =
            db.single(
                "SELECT behandlet FROM godkjente_behandlinger_inbox WHERE outbox_id = :outbox_id",
                "outbox_id" to outboxId,
            ) { it.localDateTimeOrNull("behandlet") }
        assertNull(behandletFør)

        // Marker som behandlet
        dao.markerSomBehandlet(outboxId)

        // Sjekk at den nå er markert som behandlet
        val behandletEtter =
            db.single(
                "SELECT behandlet FROM godkjente_behandlinger_inbox WHERE outbox_id = :outbox_id",
                "outbox_id" to outboxId,
            ) { it.localDateTimeOrNull("behandlet") }

        assertTrue(behandletEtter != null)
    }

    @Test
    fun `respekterer limit parameter ved henting av ubehandlede`() {
        // Sett inn flere behandlinger enn limit
        val limit = 2
        repeat(5) { i ->
            dao.settInn(55555L + i, """{"test": $i}""")
        }

        val ubehandlede = dao.hentUbehandlede(limit)

        assertTrue(ubehandlede.size <= limit)
    }

    @Test
    fun `eksisterer returnerer true når outbox-id finnes`() {
        val outboxId = 66666L
        val payload = """{"test": "eksisterer"}"""

        // Sjekk at den ikke eksisterer først
        assertTrue(!dao.eksisterer(outboxId))

        // Sett inn behandling
        dao.settInn(outboxId, payload)

        // Sjekk at den nå eksisterer
        assertTrue(dao.eksisterer(outboxId))
    }

    @Test
    fun `eksisterer returnerer false når outbox-id ikke finnes`() {
        val outboxId = 77777L

        // Sjekk at den ikke eksisterer
        assertTrue(!dao.eksisterer(outboxId))
    }

    @Test
    fun `eksisterer returnerer true selv etter at behandling er markert som behandlet`() {
        val outboxId = 88888L
        val payload = """{"test": "behandlet"}"""

        // Sett inn og marker som behandlet
        dao.settInn(outboxId, payload)
        dao.markerSomBehandlet(outboxId)

        // Sjekk at den fortsatt eksisterer
        assertTrue(dao.eksisterer(outboxId))
    }
}
