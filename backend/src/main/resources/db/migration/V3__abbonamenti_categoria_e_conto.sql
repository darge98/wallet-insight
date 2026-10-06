-- Categoria e conto di un abbonamento: dove l'addebito cadrà, entrambi facoltativi.
--
-- Le foreign key sono composte con `user_id`, come quelle dei budget: un
-- abbonamento non può puntare alla categoria o al conto di un altro utente nemmeno
-- per un errore del codice. Con una delle due colonne a null il vincolo non si
-- applica (MATCH SIMPLE), ed è ciò che rende la scelta facoltativa.

-- Bersaglio della foreign key composta qui sotto, come `unique (id, user_id)` di categories.
alter table accounts add constraint accounts_id_user_id_key unique (id, user_id);

alter table subscriptions
    add column category_id uuid,
    add column account_id  uuid,
    -- Senza cascata: togliere una categoria che un abbonamento usa deve fallire,
    -- non lasciarlo senza. L'unione di categorie sposta prima gli abbonamenti.
    add foreign key (category_id, user_id) references categories (id, user_id),
    add foreign key (account_id, user_id) references accounts (id, user_id);
