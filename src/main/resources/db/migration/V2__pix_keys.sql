create table pix_keys (
    id         uuid primary key,
    account_id uuid        not null references accounts (id),
    type       varchar(8)  not null check (type in ('EMAIL', 'PHONE', 'CPF', 'RANDOM')),
    -- stored normalized: lower-case e-mail, +55 phone, 11-digit CPF, lower-case UUID
    value      varchar(77) not null,
    created_at timestamptz not null default now(),
    constraint one_owner_per_key unique (type, value)
);

create index pix_keys_by_account on pix_keys (account_id);
