ALTER TABLE IF EXISTS tg.chat
    ADD COLUMN IF NOT EXISTS month_start integer;