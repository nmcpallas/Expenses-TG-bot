CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.processed_events
(
    event_id     uuid primary key,
    event_type   varchar(128) not null,
    processed_at timestamptz    not null default now()
);

CREATE TABLE IF NOT EXISTS tg.monthly_report_job
(
    id           uuid primary key,
    chat_id      bigint      not null references tg.chat (id),
    period_start date        not null,
    period_end   date        not null,
    event_id     uuid        not null unique,
    report_id    uuid,
    status       varchar(32) not null,
    created_at   timestamptz not null default now(),
    delivered_at timestamptz,

    CONSTRAINT uq_monthly_report_job_period UNIQUE (chat_id, period_start, period_end),
    CONSTRAINT chk_monthly_report_job_period CHECK (period_start < period_end)
);

CREATE INDEX IF NOT EXISTS idx_monthly_report_job_report_id
    ON tg.monthly_report_job(report_id);
