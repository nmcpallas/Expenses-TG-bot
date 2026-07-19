CREATE SCHEMA IF NOT EXISTS billing;
CREATE SCHEMA IF NOT EXISTS access;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE IF EXISTS tg.user
    ADD COLUMN IF NOT EXISTS global_role varchar(32) NOT NULL DEFAULT 'USER';

CREATE TABLE IF NOT EXISTS tg.chat_member
(
    id         uuid primary key,
    chat_id    bigint      not null references tg.chat (id),
    user_id    bigint      not null references tg.user (id),
    role       varchar(32) not null,

    created_at timestamp   not null default now(),
    updated_at timestamp   not null default now(),

    CONSTRAINT uq_chat_member_chat_user UNIQUE (chat_id, user_id)
);

INSERT INTO tg.chat_member (id, chat_id, user_id, role)
SELECT gen_random_uuid(), chat.id, chat.user_id, 'OWNER'
FROM tg.chat chat
WHERE NOT EXISTS (
    SELECT 1
    FROM tg.chat_member member
    WHERE member.chat_id = chat.id
      AND member.user_id = chat.user_id
);

CREATE TABLE IF NOT EXISTS billing.subscription
(
    id            uuid primary key,
    owner_user_id bigint      not null references tg.user (id),
    type          varchar(32) not null,
    plan          varchar(32) not null,
    status        varchar(32) not null,
    valid_until   timestamp,

    created_at    timestamp   not null default now(),
    updated_at    timestamp   not null default now()
);

CREATE TABLE IF NOT EXISTS billing.subscription_member
(
    id              uuid primary key,
    subscription_id uuid        not null references billing.subscription (id),
    user_id         bigint      not null references tg.user (id),
    role            varchar(32) not null,

    created_at      timestamp   not null default now(),
    updated_at      timestamp   not null default now(),

    CONSTRAINT uq_subscription_member_subscription_user UNIQUE (subscription_id, user_id)
);

CREATE TABLE IF NOT EXISTS billing.subscription_chat
(
    id              uuid primary key,
    subscription_id uuid      not null references billing.subscription (id),
    chat_id         bigint    not null references tg.chat (id),

    created_at      timestamp not null default now(),
    updated_at      timestamp not null default now(),

    CONSTRAINT uq_subscription_chat_subscription_chat UNIQUE (subscription_id, chat_id)
);

CREATE TABLE IF NOT EXISTS access.entitlement
(
    id           uuid primary key,
    subject_type varchar(32)  not null,
    subject_id   varchar(64)  not null,
    feature      varchar(64)  not null,
    value_type   varchar(32)  not null,
    value        varchar(255) not null,
    source       varchar(32)  not null,
    valid_from   timestamp,
    valid_until  timestamp,
    enabled      boolean      not null default true,

    created_at   timestamp    not null default now(),
    updated_at   timestamp    not null default now()
);

CREATE INDEX IF NOT EXISTS idx_chat_member_chat_user
    ON tg.chat_member(chat_id, user_id);

CREATE INDEX IF NOT EXISTS idx_subscription_member_user
    ON billing.subscription_member(user_id);

CREATE INDEX IF NOT EXISTS idx_subscription_chat_chat
    ON billing.subscription_chat(chat_id);

CREATE INDEX IF NOT EXISTS idx_entitlement_subject_feature
    ON access.entitlement(subject_type, subject_id, feature)
    WHERE enabled = true;
