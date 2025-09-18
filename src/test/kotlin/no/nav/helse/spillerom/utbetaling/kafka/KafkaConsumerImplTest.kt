package no.nav.helse.spillerom.utbetaling.kafka

import kotliquery.Row
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.MedDataSource
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.TestDataSource
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class KafkaConsumerImplTest {
    private val db = MedDataSource(TestDataSource.dbModule.dataSource)
    private val dao = GodkjenteBehandlingerInboxDao(TestDataSource.dbModule.dataSource)
    private lateinit var meldingProsesserer: MeldingProsesserer

    @BeforeEach
    fun setOpp() {
        TestDataSource.resetDatasource()
        meldingProsesserer = MeldingProsesserer(dao)
    }

    @Test
    fun `prosesserer godkjent behandling og lagrer i inbox`() {
        val outboxId = 12345L
        val payload = """{"id": "test-id", "status": "GODKJENT", "data": "test"}"""
        val record = ConsumerRecord("test-topic", 0, 0L, "test-key", payload)
        record.headers().add("outbox-id", outboxId.toString().toByteArray())

        // Prosesser melding
        meldingProsesserer.prosesserMelding(record)

        // Sjekk at den ble lagret i inbox
        assertTrue(dao.eksisterer(outboxId))

        val lagretPayload =
            db.single(
                "SELECT payload FROM godkjente_behandlinger_inbox WHERE outbox_id = :outbox_id",
                "outbox_id" to outboxId,
            ) { rs: Row -> rs.string("payload") }

        assertEquals(payload, lagretPayload)
    }

    @Test
    fun `ignorerer melding med status som ikke er GODKJENT`() {
        val outboxId = 23456L
        val payload = """{"id": "test-id", "status": "UNDER_BEHANDLING", "data": "test"}"""
        val record = ConsumerRecord("test-topic", 0, 0L, "test-key", payload)
        record.headers().add("outbox-id", outboxId.toString().toByteArray())

        // Prosesser melding
        meldingProsesserer.prosesserMelding(record)

        // Sjekk at den ikke ble lagret
        assertFalse(dao.eksisterer(outboxId))
    }

    @Test
    fun `feiler hvis outbox-id header mangler`() {
        val payload = """{"id": "test-id", "status": "GODKJENT", "data": "test"}"""
        // Ingen outbox-id header
        val record = ConsumerRecord("test-topic", 0, 0L, "test-key", payload)

        // Sjekk at det kastes exception
        assertThrows(IllegalStateException::class.java) {
            meldingProsesserer.prosesserMelding(record)
        }
    }

    @Test
    fun `feiler hvis outbox-id header har ugyldig format`() {
        val payload = """{"id": "test-id", "status": "GODKJENT", "data": "test"}"""
        val record = ConsumerRecord("test-topic", 0, 0L, "test-key", payload)
        record.headers().add("outbox-id", "ikke-et-tall".toByteArray())

        // Sjekk at det kastes exception
        assertThrows(IllegalStateException::class.java) {
            meldingProsesserer.prosesserMelding(record)
        }
    }

    @Test
    fun `hopper over duplikat hvis outbox-id allerede eksisterer`() {
        val outboxId = 34567L
        val payload = """{"id": "test-id", "status": "GODKJENT", "data": "test"}"""
        val record = ConsumerRecord("test-topic", 0, 0L, "test-key", payload)
        record.headers().add("outbox-id", outboxId.toString().toByteArray())

        // Sett inn første gang
        dao.settInn(outboxId, payload)
        assertTrue(dao.eksisterer(outboxId))

        // Prosesser samme melding igjen - skal ikke feile
        assertDoesNotThrow {
            meldingProsesserer.prosesserMelding(record)
        }

        // Sjekk at det fortsatt bare er én post
        val antall =
            db.single(
                "SELECT COUNT(*) FROM godkjente_behandlinger_inbox WHERE outbox_id = :outbox_id",
                "outbox_id" to outboxId,
            ) { rs: Row -> rs.int(1) }

        assertEquals(1, antall)
    }

    @Test
    fun `feiler hvis JSON payload er ugyldig`() {
        val outboxId = 45678L
        val ugyldigPayload = """{"id": "test-id", "status": "GODKJENT", "data": "test""" // Mangler avsluttende }
        val record = ConsumerRecord("test-topic", 0, 0L, "test-key", ugyldigPayload)
        record.headers().add("outbox-id", outboxId.toString().toByteArray())

        // Sjekk at det kastes exception
        assertThrows(Exception::class.java) {
            meldingProsesserer.prosesserMelding(record)
        }
    }

    @Test
    fun `feiler hvis status felt mangler i JSON`() {
        val outboxId = 56789L
        val payload = """{"id": "test-id", "data": "test"}""" // Mangler status felt
        val record = ConsumerRecord("test-topic", 0, 0L, "test-key", payload)

        // Prosesser melding - skal ikke feile, men ignorere
        assertDoesNotThrow {
            meldingProsesserer.prosesserMelding(record)
        }

        // Sjekk at den ikke ble lagret
        assertFalse(dao.eksisterer(outboxId))
    }

    @Test
    fun `prosesserer flere meldinger med forskjellige statuser`() {
        val outboxId1 = 67890L
        val outboxId2 = 78901L
        val outboxId3 = 89012L

        val godkjentPayload = """{"id": "test-1", "status": "GODKJENT", "data": "test1"}"""
        val underBehandlingPayload = """{"id": "test-2", "status": "UNDER_BEHANDLING", "data": "test2"}"""
        val tilBeslutningPayload = """{"id": "test-3", "status": "TIL_BESLUTNING", "data": "test3"}"""

        val record1 = ConsumerRecord("test-topic", 0, 0L, "key1", godkjentPayload)
        val record2 = ConsumerRecord("test-topic", 0, 1L, "key2", underBehandlingPayload)
        val record3 = ConsumerRecord("test-topic", 0, 2L, "key3", tilBeslutningPayload)

        record1.headers().add("outbox-id", outboxId1.toString().toByteArray())
        record2.headers().add("outbox-id", outboxId2.toString().toByteArray())
        record3.headers().add("outbox-id", outboxId3.toString().toByteArray())

        // Prosesser alle meldinger
        meldingProsesserer.prosesserMelding(record1)
        meldingProsesserer.prosesserMelding(record2)
        meldingProsesserer.prosesserMelding(record3)

        // Sjekk at kun den godkjente ble lagret
        assertTrue(dao.eksisterer(outboxId1))
        assertFalse(dao.eksisterer(outboxId2))
        assertFalse(dao.eksisterer(outboxId3))
    }
}
