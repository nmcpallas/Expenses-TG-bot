ALTER TABLE IF EXISTS tg.category
    ADD COLUMN IF NOT EXISTS spending_limit numeric(12, 2);

ALTER TABLE IF EXISTS tg.chat
    ADD COLUMN IF NOT EXISTS weekly_report_enabled boolean NOT NULL DEFAULT true;

ALTER TABLE IF EXISTS tg.chat
    ADD COLUMN IF NOT EXISTS unusual_notifications_enabled boolean NOT NULL DEFAULT true;
