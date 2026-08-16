ALTER TABLE IF EXISTS tg.category
    ADD COLUMN IF NOT EXISTS archived boolean NOT NULL DEFAULT false;

UPDATE tg.chat chat
SET month_limit = COALESCE(
        (
            SELECT SUM(category.spending_limit)
            FROM tg.category category
            WHERE category.chat_id = chat.id
              AND category.archived = false
              AND category.spending_limit IS NOT NULL
        ),
        0
    );
