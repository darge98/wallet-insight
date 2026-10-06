-- ---------------------------------------------------------------------------
-- subscriptions — gli abbonamenti (`core/subscriptions`), scritti dall'utente.
--
-- Non si salvano né il prossimo addebito né i costi equivalenti: dipendono da
-- «oggi», e una colonna sarebbe vera solo il giorno in cui è stata scritta.
-- ---------------------------------------------------------------------------
create table subscriptions (
    id            uuid        primary key,
    user_id       uuid        not null references users (id) on delete cascade,
    name          varchar(80) not null,
    amount_cents  bigint      not null check (amount_cents > 0),
    cadence_every smallint    not null check (cadence_every between 1 and 99),
    cadence_unit  varchar(5)  not null check (cadence_unit in ('week', 'month', 'year')),
    -- Il primo addebito: i successivi si contano sempre da qui, non dal precedente.
    start_date    date        not null,
    -- L'ultimo giorno in cui può cadere un addebito; null finché non è disdetto.
    end_date      date        check (end_date >= start_date),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create index subscriptions_per_utente on subscriptions (user_id);
