ALTER TABLE IF EXISTS tg.chat
    ALTER COLUMN month_start SET DEFAULT 1;

UPDATE tg.chat
SET month_start = 1
WHERE month_start IS NULL;

ALTER TABLE IF EXISTS tg.chat
    ALTER COLUMN month_start SET NOT NULL;
