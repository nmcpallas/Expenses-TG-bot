CREATE TABLE IF NOT EXISTS tg.weekly_report_delivery
(
    id           uuid primary key,
    chat_id      bigint      not null references tg.chat (id),
    period_start date        not null,
    period_end   date        not null,
    event_id     uuid        not null unique,
    report_id    uuid,
    status       varchar(32) not null,
    requested_at timestamptz not null,
    delivered_at timestamptz,

    CONSTRAINT uq_weekly_report_delivery_period UNIQUE (chat_id, period_start, period_end),
    CONSTRAINT chk_weekly_report_delivery_period CHECK (period_start < period_end)
);
