-- Avisos por Telegram: cada persona enlaza su chat con el bot de la plataforma. Por usuario, no por negocio.
CREATE TABLE telegram_links (
    user_id   UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    chat_id   BIGINT      NOT NULL UNIQUE,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Códigos de un solo uso para enlazar (se guarda solo su hash; caducan en 15 minutos).
CREATE TABLE telegram_link_codes (
    code_hash  VARCHAR(64) PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_telegram_link_codes_user ON telegram_link_codes (user_id);
