ALTER TABLE tg.expense
    ALTER COLUMN amount TYPE numeric(12,2)
        USING amount::numeric;

ALTER TABLE tg.chat
    ALTER COLUMN month_limit TYPE numeric(12,2)
        USING month_limit::numeric;