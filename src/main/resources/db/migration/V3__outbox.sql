-- Transactional outbox: an event is written in the same transaction as the change it
-- describes, then a publisher relays it to RabbitMQ. Either both happen or neither does.
create table outbox_events (
    id           uuid primary key,
    type         varchar(64)  not null,
    aggregate_id uuid         not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    attempts     int          not null default 0,
    last_error   varchar(500)
);

create index outbox_pending on outbox_events (created_at) where published_at is null;
