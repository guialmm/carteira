create table users (
    id            uuid primary key,
    email         varchar(254) not null unique,
    name          varchar(120) not null,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null default now()
);

-- FUNDING is the system account deposits come from. Its balance goes negative,
-- so the sum of every balance is always zero (double-entry bookkeeping).
create table accounts (
    id            uuid primary key,
    owner_id      uuid unique references users (id),
    kind          varchar(16) not null check (kind in ('CUSTOMER', 'FUNDING')),
    balance_cents bigint      not null default 0,
    created_at    timestamptz not null default now(),
    constraint customer_has_owner check (kind <> 'CUSTOMER' or owner_id is not null),
    constraint customer_balance_non_negative check (kind <> 'CUSTOMER' or balance_cents >= 0)
);

insert into accounts (id, kind) values ('00000000-0000-0000-0000-000000000001', 'FUNDING');

create table transfers (
    id                     uuid primary key,
    kind                   varchar(16) not null check (kind in ('TRANSFER', 'DEPOSIT')),
    source_account_id      uuid        not null references accounts (id),
    destination_account_id uuid        not null references accounts (id),
    -- the customer account that asked for it: idempotency keys are scoped to it
    initiator_account_id   uuid        not null references accounts (id),
    amount_cents           bigint      not null check (amount_cents > 0),
    description            varchar(140),
    idempotency_key        varchar(64) not null,
    request_hash           varchar(64) not null,
    created_at             timestamptz not null default now(),
    constraint different_accounts check (source_account_id <> destination_account_id),
    constraint one_transfer_per_key unique (initiator_account_id, idempotency_key)
);

create table ledger_entries (
    id                  bigint generated always as identity primary key,
    transfer_id         uuid        not null references transfers (id),
    account_id          uuid        not null references accounts (id),
    amount_cents        bigint      not null check (amount_cents <> 0),
    balance_after_cents bigint      not null,
    created_at          timestamptz not null default now()
);

create index ledger_entries_by_account on ledger_entries (account_id, id desc);
create index transfers_by_source on transfers (source_account_id, created_at desc);
create index transfers_by_destination on transfers (destination_account_id, created_at desc);
