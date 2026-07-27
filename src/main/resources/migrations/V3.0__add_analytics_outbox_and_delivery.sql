CREATE TABLE IF NOT EXISTS tg.outbox_event
(
    id            uuid primary key,
    event_id      uuid         not null unique,
    routing_key   varchar(160) not null,
    payload       text         not null,
    created_at    timestamptz  not null,
    published_at  timestamptz,
    attempt_count integer      not null default 0
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_pending
    ON tg.outbox_event (created_at)
    WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS tg.extreme_expense_delivery
(
    event_id     uuid primary key,
    expense_id   uuid        not null unique,
    chat_id      bigint      not null references tg.chat (id),
    status       varchar(32) not null,
    claimed_at   timestamptz not null,
    delivered_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_expense_chat_created_at
    ON tg.expense (chat_id, created_at);

CREATE INDEX IF NOT EXISTS idx_expense_chat_category_created_at
    ON tg.expense (chat_id, category_id, created_at DESC);
