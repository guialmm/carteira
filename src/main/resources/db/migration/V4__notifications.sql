-- Owned by the notification consumer. No foreign keys into the wallet tables on purpose:
-- it learns about accounts only from events, so it could move to its own service and database.
create table processed_events (
    consumer     varchar(64) not null,
    event_id     uuid        not null,
    processed_at timestamptz not null default now(),
    primary key (consumer, event_id)
);

create table notifications (
    id           uuid primary key,
    account_id   uuid         not null,
    transfer_id  uuid         not null,
    kind         varchar(16)  not null check (kind in ('RECEIVED', 'SENT', 'DEPOSIT')),
    message      varchar(200) not null,
    amount_cents bigint       not null,
    created_at   timestamptz  not null default now()
);

create index notifications_by_account on notifications (account_id, created_at desc);
