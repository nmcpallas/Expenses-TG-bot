create schema if not exists tg;

create table if not exists tg.user
(
    id         bigint primary key,

    created_at timestamp not null default now(),
    version    bigint    not null default 0
);

create table if not exists tg.chat
(
    id          bigint primary key,
    user_id     bigint    not null references tg.user (id),
    month_limit double precision   not null default 0,

    created_at  timestamp not null default now(),
    version     bigint    not null default 0
);

create table if not exists tg.expense
(
    id          uuid primary key,
    chat_id     bigint           not null references tg.chat (id),
    amount      double precision not null check (amount > 0),
    description varchar(255),

    created_at  timestamp        not null default now(),
    version     bigint           not null default 0
);
