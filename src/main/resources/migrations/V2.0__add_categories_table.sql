CREATE TABLE IF NOT EXISTS tg.category
(
    id         uuid primary key,
    chat_id    bigint       references tg.chat (id),
    name       VARCHAR(255) NOT NULL default 'Общие',

    created_at timestamp    not null default now(),
    updated_at timestamp    not null default now(),
    version    bigint       not null default 0
);

ALTER TABLE IF EXISTS tg.expense
    ADD IF NOT EXISTS category_id uuid references tg.category (id);

ALTER TABLE IF EXISTS tg.expense
    ADD IF NOT EXISTS updated_at timestamp    not null default now();

ALTER TABLE IF EXISTS tg.chat
    ADD IF NOT EXISTS updated_at timestamp    not null default now();

ALTER TABLE IF EXISTS tg.user
    ADD IF NOT EXISTS updated_at timestamp    not null default now();