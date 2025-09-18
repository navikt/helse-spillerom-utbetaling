package no.nav.helse.spillerom.utbetaling.kafka

import kotliquery.Session
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.MedDataSource
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.MedSession
import no.nav.helse.spillerom.utbetaling.infrastruktur.db.QueryRunner
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class GodkjenteBehandlingerInboxDao private constructor(private val db: QueryRunner) {
    constructor(dataSource: DataSource) : this(
        MedDataSource(
            dataSource,
        ),
    )
    constructor(session: Session) : this(
        MedSession(
            session,
        ),
    )

    fun settInn(
        outboxId: Long,
        payload: String,
    ) {
        db.update(
            """
            INSERT INTO godkjente_behandlinger_inbox (outbox_id, payload)
            VALUES (:outbox_id, :payload)
            """.trimIndent(),
            "outbox_id" to outboxId,
            "payload" to payload,
        )
    }

    fun hentUbehandlede(limit: Int = 100): List<GodkjentBehandlingInbox> {
        return db.list(
            """
            SELECT outbox_id, payload, opprettet
            FROM godkjente_behandlinger_inbox
            WHERE behandlet IS NULL
            ORDER BY outbox_id ASC
            LIMIT :limit
            """.trimIndent(),
            "limit" to limit,
        ) { rs ->
            GodkjentBehandlingInbox(
                outboxId = rs.long("outbox_id"),
                payload = rs.string("payload"),
                opprettet = rs.localDateTime("opprettet"),
            )
        }
    }

    fun markerSomBehandlet(outboxId: Long) {
        db.update(
            """
            UPDATE godkjente_behandlinger_inbox
            SET behandlet = NOW()
            WHERE outbox_id = :outbox_id
            """.trimIndent(),
            "outbox_id" to outboxId,
        )
    }

    fun eksisterer(outboxId: Long): Boolean {
        return db.list(
            """
            SELECT 1 FROM godkjente_behandlinger_inbox
            WHERE outbox_id = :outbox_id
            LIMIT 1
            """.trimIndent(),
            "outbox_id" to outboxId,
        ) { rs ->
            rs.int(1)
        }.isNotEmpty()
    }
}

data class GodkjentBehandlingInbox(
    val outboxId: Long,
    val payload: String,
    val opprettet: LocalDateTime,
)
