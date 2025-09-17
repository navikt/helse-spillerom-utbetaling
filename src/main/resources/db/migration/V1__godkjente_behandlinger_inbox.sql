CREATE TABLE godkjente_behandlinger_inbox
(
    outbox_id  BIGINT                      NOT NULL PRIMARY KEY,
    payload    TEXT                        NOT NULL,
    opprettet  TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    behandlet  TIMESTAMP(6) WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_godkjente_behandlinger_inbox_ubehandlet
    ON godkjente_behandlinger_inbox (behandlet);
