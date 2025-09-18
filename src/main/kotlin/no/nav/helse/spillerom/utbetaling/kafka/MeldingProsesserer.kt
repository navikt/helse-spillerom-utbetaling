package no.nav.helse.spillerom.utbetaling.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MeldingProsesserer(
    private val inboxDao: GodkjenteBehandlingerInboxDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(MeldingProsesserer::class.java)
    private val objectMapper = ObjectMapper()

    fun prosesserMelding(record: ConsumerRecord<String, String>) {
        logger.debug("Prosesserer melding med key: ${record.key()}, offset: ${record.offset()}")

        // Parse payload for å sjekke status
        val payload = record.value()
        val behandling = objectMapper.readTree(payload)
        val status = behandling.get("status")?.asText()

        if (status == "GODKJENT") {
            // Hent outbox-id fra headers - feil hvis den mangler
            val outboxIdHeader = record.headers().lastHeader("outbox-id")
            if (outboxIdHeader == null) {
                throw IllegalStateException("Mangler outbox-id header i melding med key: ${record.key()}, offset: ${record.offset()}")
            }

            val outboxId =
                try {
                    String(outboxIdHeader.value()).toLong()
                } catch (e: NumberFormatException) {
                    throw IllegalStateException("Ugyldig outbox-id format i header: ${String(outboxIdHeader.value())}", e)
                }

            // Sjekk om outbox-id allerede eksisterer i databasen
            if (inboxDao.eksisterer(outboxId)) {
                logger.debug("Outbox-id $outboxId eksisterer allerede i inbox - hopper over melding")
                return
            }

            // Lagre i inbox
            inboxDao.settInn(outboxId, payload)
            logger.info("Lagret godkjent behandling i inbox med outbox-id: $outboxId")
        } else {
            logger.debug("Ignorerer melding med status: $status")
        }
    }
}
