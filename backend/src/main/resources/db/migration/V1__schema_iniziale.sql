-- Lo schema di Margine, in una migrazione sola.
--
-- Le tabelle nascono già nella forma finale: i passi con cui ci si è arrivati
-- avrebbero raccontato una storia a un database che non l'ha mai vissuta. Dove una
-- scelta è stata rovesciata strada facendo resta la ragione, non il giro.
--
-- Due regole valgono ovunque e non si ripetono tabella per tabella:
--
-- * **Gli identificatori li genera l'applicazione**, non il database: UUIDv7 da
--   `shared/identifier/Uuids`. Niente sequenze, così restano stabili fra gli
--   ambienti e gli indici B-tree ricevono inserimenti ordinati per tempo. È anche
--   il motivo per cui nessuna migrazione può riempire una colonna di identificatori.
-- * **I valori testuali sono quelli del contratto HTTP** — `'budget-bakers'`,
--   `'current-account'`, `'wait-for-assign'` — in kebab-case, mai il nome della
--   costante Java: il database non deve cambiare se una enum viene rinominata.
--
-- L'ordine di creazione è quello delle dipendenze: `users` regge tutto, i movimenti
-- puntano a conti e categorie, i budget alle categorie.


-- ---------------------------------------------------------------------------
-- users — il modulo `core/users`.
-- ---------------------------------------------------------------------------
create table users (
    id                       uuid         primary key,
    first_name               varchar(80)  not null,
    last_name                varchar(80),
    -- unique ammette più NULL in PostgreSQL: l'email resta opzionale ma, se presente, distingue.
    email                    varchar(160) unique,
    -- Impostazioni: poche, obbligatorie e lette sempre insieme all'utente,
    -- per questo sono colonne della stessa tabella e non una 1:1 separata.
    time_zone                varchar(64)  not null,
    language                 varchar(2)   not null,
    default_dashboard_period varchar(20)  not null,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now()
);


-- ---------------------------------------------------------------------------
-- import_connections — le sorgenti collegate da un utente (`core/ingestion`).
--
-- Una riga per coppia (utente, sorgente): un utente può collegare più sorgenti,
-- ma una sola volta ciascuna — riconfigurarla sostituisce le credenziali, non
-- aggiunge un legame. Il vincolo di unicità è ciò che rende vera quella frase.
-- ---------------------------------------------------------------------------
create table import_connections (
    id               uuid        primary key,
    -- on delete cascade: le credenziali non sopravvivono all'utente che le ha date.
    user_id          uuid        not null references users (id) on delete cascade,
    source           varchar(40) not null,
    -- Testo cifrato con AES-256-GCM dall'applicazione (infrastructure/crypto): la
    -- chiave sta nell'ambiente, quindi il solo dump del database non lo rivela.
    -- La lunghezza è libera: dipende dal segreto e dal formato, non dallo schema.
    encrypted_secret text        not null,
    enabled          boolean     not null default true,
    -- Quando l'utente ha configurato le credenziali correnti: un fatto di dominio,
    -- distinto da updated_at, che è traccia tecnica della riga.
    configured_at    date        not null,

    -- Due colonne per due domande diverse, ed è la distinzione che conta di più
    -- in tutta questa tabella.
    --
    -- `last_record_date` è la data del *dato* più recente portato dentro: è il
    -- segnaposto da cui riparte l'import incrementale. `last_run_at` è l'istante
    -- in cui si è guardato. Le due divergono spesso — un import che gira oggi e
    -- non trova nulla sposta la seconda e non la prima — ed è esattamente ciò che
    -- serve: usare l'istante di esecuzione come segnaposto farebbe saltare i
    -- movimenti registrati in ritardo su giorni già passati, e non tenerlo
    -- lascerebbe senza risposta l'utente che chiede «ho i dati aggiornati?».
    --
    -- Entrambe nullable e senza default: `null` significa «mai importata» e «mai
    -- arrivato in fondo», e da dove partire la prima volta lo decide la sorgente,
    -- non lo schema. `last_run_at` si muove solo a import riuscito: un tentativo
    -- fallito non è una sincronizzazione, e registrarlo farebbe leggere
    -- «aggiornato» a chi non lo è. `timestamptz` e non `date` perché qui l'ora è
    -- un fatto di esecuzione, non di calendario come le date dei movimenti.
    last_record_date date,
    last_run_at      timestamptz,
    -- Quando la sorgente ha rifiutato le credenziali (un 401): è l'unico errore che
    -- l'utente può risolvere da sé, incollando un token nuovo, e l'interfaccia lo dice.
    credentials_rejected_at timestamptz,

    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    unique (user_id, source)
);

comment on column import_connections.last_record_date is
    'Data del movimento più recente importato; null se la sorgente non è mai stata letta.';
comment on column import_connections.last_run_at is
    'Istante dell''ultimo import riuscito; null se non è mai arrivato in fondo. Diverso da last_record_date.';
comment on column import_connections.credentials_rejected_at is
    'Quando la sorgente ha rifiutato le credenziali; null se non è successo dall''ultimo token o dall''ultimo import riuscito.';


-- ---------------------------------------------------------------------------
-- accounts — i conti (`core/accounts`).
--
-- Un conto di Margine non è il conto di BudgetBakers: ne è la controparte.
-- `source` + `external_id` sono il riferimento al dato originale e non cambiano
-- mai, così il nostro conto resta agganciato al suo anche quando l'utente lo
-- rinomina qui dentro. È anche la chiave con cui un import riconosce ciò che ha
-- già creato, e quindi ciò che rende ripetibile l'importazione.
-- ---------------------------------------------------------------------------
create table accounts (
    id                    uuid         primary key,
    -- on delete cascade: i conti non sopravvivono all'utente che li possiede.
    user_id               uuid         not null references users (id) on delete cascade,
    source                varchar(40)  not null,
    -- L'identificativo assegnato dalla sorgente: opaco, è loro e non lo interpretiamo.
    external_id           varchar(120) not null,

    -- Due nomi, di proprietà diversa. `source_name` è come il conto si chiama
    -- nella sorgente e viene riscritto a ogni import; `name` è come si chiama in
    -- Margine e appartiene all'utente. Finché coincidono, l'utente non è
    -- intervenuto e il nome segue la sorgente; dal momento in cui divergono, un
    -- import non tocca più `name`. La regola si deduce dai due valori, senza un
    -- flag da tenere allineato a mano.
    name                  varchar(120) not null,
    source_name           varchar(120) not null,

    -- Tipo normalizzato di Margine ('current-account'). La sorgente ne dichiara
    -- di suoi e la traduzione avviene nell'ingestion.
    kind                  varchar(30)  not null,
    currency_code         varchar(3)   not null,

    -- Solo il saldo di partenza, in centesimi interi come ogni importo.
    -- Il saldo corrente NON sta qui: è la somma dei movimenti a partire da
    -- questo valore, e la calcola PostgreSQL quando serve. Una colonna con
    -- l'ultimo saldo noto invecchierebbe fra un import e l'altro, dando due
    -- verità in disaccordo senza che si sappia a quale credere.
    initial_balance_cents bigint       not null,

    -- IBAN o numero di conto come la sorgente lo espone (max 80 per specifica),
    -- assente sui conti che non ne hanno (i contanti).
    iban                  varchar(80),

    -- Il colore è dell'utente come `name`: la sorgente non ne dichiara nessuno e
    -- nessun import lo tocca. Serve a riconoscere un conto con un'occhiata in un
    -- elenco — mai da solo, sempre accanto al nome, perché il colore non è un
    -- canale che tutti leggono allo stesso modo.
    --
    -- Nullable e senza default: `null` significa «l'utente non ha scelto», e
    -- l'interfaccia mostra un segno neutro. Assegnarne uno d'ufficio a ogni conto
    -- importato darebbe sei colori decisi da noi che l'utente non ha chiesto, e
    -- nessun modo di distinguerli da una scelta vera.
    --
    -- `varchar(7)` perché è `#rrggbb`: sette caratteri esatti, il formato che un
    -- `<input type="color">` produce e che il CSS legge senza conversioni.
    color                 varchar(7),

    -- Archiviato nella sorgente: il conto resta, perché possiede movimenti
    -- storici che continuano ad arrivare. Nasconderlo è una scelta della UI.
    archived              boolean      not null default false,
    excluded_from_stats   boolean      not null default false,

    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),

    -- Un conto della sorgente ha una sola controparte qui: è ciò che rende
    -- l'import idempotente, e il vincolo è ciò che rende vera quella frase.
    unique (user_id, source, external_id)
);

comment on column accounts.external_id is
    'Identificativo del conto nella sorgente: riferimento stabile al dato originale.';
comment on column accounts.source_name is
    'Nome nella sorgente, riscritto a ogni import; `name` resta dell''utente se l''ha cambiato.';
comment on column accounts.initial_balance_cents is
    'Saldo di partenza. Il saldo corrente si calcola dai movimenti, non si memorizza.';
comment on column accounts.color is
    'Colore scelto dall''utente in formato #rrggbb; null se non ne ha scelto uno. Nessun import lo tocca.';


-- ---------------------------------------------------------------------------
-- categories — le categorie di Margine (`core/categories`).
--
-- Sono dell'utente, non della sorgente: un albero a due livelli, macro →
-- sottocategoria, che parte dall'elenco di base (`DefaultCategories`, copiato per
-- utente al primo accesso o al primo import) e che l'utente rinomina, sposta, crea e
-- unisce. I movimenti stanno solo nelle sottocategorie.
-- ---------------------------------------------------------------------------
create table categories (
    id           uuid        primary key,
    -- on delete cascade: le categorie non sopravvivono all'utente che le possiede.
    user_id      uuid        not null references users (id) on delete cascade,
    -- null per una macro. La macro dev'essere dello stesso utente: lo garantisce la
    -- foreign key composta qui sotto, non il codice.
    parent_id    uuid,
    name         varchar(80) not null,
    -- `#rrggbb`, scelto dall'utente; null se non ne ha scelto uno.
    color        varchar(7),
    -- La voce dell'elenco di base da cui è nata ('cibo/spesa'): è ciò con cui
    -- l'import la ritrova anche rinominata. Null se creata a mano.
    template_key varchar(60),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    -- Bersaglio delle foreign key composte, qui e in `budget_categories`.
    unique (id, user_id),
    unique (user_id, template_key),
    foreign key (parent_id, user_id) references categories (id, user_id)
);

create index categories_per_utente on categories (user_id);

comment on column categories.template_key is
    'Voce dell''elenco di base da cui la categoria è nata; null se l''ha creata l''utente.';


-- ---------------------------------------------------------------------------
-- source_categories — le categorie come le dichiara una sorgente.
--
-- Una categoria della sorgente è la controparte di quella là, come un conto:
-- `source` + `external_id` la identificano e non cambiano mai. Ognuna è
-- **agganciata** a una categoria di Margine, e l'import classifica attraverso
-- l'aggancio: riagganciarla sposta anche i movimenti già importati rimasti dove
-- l'aggancio li aveva messi. Il primo aggancio lo decide `BudgetBakersCategories`.
--
-- Che fossero un'anagrafica e non un'etichetta ripetuta è misurato, non
-- supposto: nei 1678 movimenti importati da un utente reale ogni movimento ha
-- una categoria, le categorie distinte sono 70 e il legame fra identificativo e
-- nome è 1:1.
-- ---------------------------------------------------------------------------
create table source_categories (
    id                 uuid         primary key,
    user_id            uuid         not null references users (id) on delete cascade,
    source             varchar(40)  not null,
    external_id        varchar(120) not null,
    -- Il nome nella sorgente, riscritto a ogni import: quello che l'utente vede è
    -- il nome della categoria di Margine a cui è agganciata.
    name               varchar(120) not null,
    -- Il raggruppamento dichiarato dalla sorgente ('food_and_drinks'), grezzo: un
    -- gruppo nuovo dev'essere leggibile il giorno che arriva, non far fallire un import.
    source_group       varchar(60),
    -- La categoria della sorgente da cui deriva una personale: l'aggancio la segue.
    parent_external_id varchar(120),
    -- `on delete set null`: unire due categorie di Margine riaggancia prima, quindi
    -- un aggancio orfano è un'eccezione da riparare, non un import da far fallire.
    category_id        uuid         references categories (id) on delete set null,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    unique (user_id, source, external_id)
);

create index source_categories_per_utente on source_categories (user_id, source);

comment on column source_categories.external_id is
    'Identificativo della categoria nella sorgente: riferimento stabile al dato originale.';
comment on column source_categories.source_group is
    'Raggruppamento dichiarato dalla sorgente (es. food_and_drinks), grezzo e non normalizzato.';
comment on column source_categories.category_id is
    'La categoria di Margine a cui è agganciata: l''import classifica i movimenti attraverso questa.';


-- ---------------------------------------------------------------------------
-- movements — il dato per cui tutto il resto esiste (`core/movements`).
--
-- Come i conti, un movimento di Margine è la controparte di un movimento della
-- sorgente: `source` + `external_id` lo identificano là e non cambiano mai. È
-- ciò che rende ripetibile l'import, e qui conta più che sui conti — la finestra
-- di lettura rilegge di proposito il giorno già importato, quindi ogni giro
-- ripresenta movimenti già visti.
--
-- **Un campo, un valore.** Descrizione, pagatore/pagante e categoria hanno una
-- colonna sola, non una della sorgente e una dell'utente. La forma a due colonne
-- è stata provata e scartata, ed è utile sapere perché: con «`null` significa
-- segui la sorgente» non c'era modo di dire «qui non ci va niente», e svuotare
-- una descrizione rimetteva in mostra il tracciato della banca. Dare un
-- significato alla stringa vuota spostava il problema invece di toglierlo —
-- il campo restava vuoto, ma tornare a `null` non era più esprimibile.
--
-- La forma giusta è più semplice: **l'import scrive nel campo che si legge**,
-- una volta, quando il movimento entra; da lì in poi quel campo è il dato e
-- nessun import lo tocca più. Niente ripiego da calcolare in lettura, quindi
-- `null` vuol dire l'unica cosa che ha sempre voluto dire ovunque: qui non c'è
-- niente. Il prezzo, detto chiaro: il valore con cui il movimento era arrivato
-- non resta da parte, e riscrivere una descrizione la sostituisce.
-- ---------------------------------------------------------------------------
create table movements (
    id                   uuid         primary key,
    -- on delete cascade su entrambi: un movimento non sopravvive né all'utente
    -- né al conto su cui poggia.
    user_id              uuid         not null references users (id) on delete cascade,
    account_id           uuid         not null references accounts (id) on delete cascade,

    source               varchar(40)  not null,
    external_id          varchar(120) not null,

    -- Importo con segno, in centesimi interi: negativo in uscita, positivo in
    -- entrata. Il segno è il dato; `direction` è ciò che la sorgente *dichiara*.
    -- È nella valuta del conto, ed è su questo che si calcola il saldo del conto.
    amount_cents         bigint       not null,
    currency_code        varchar(3)   not null,
    -- Lo stesso movimento in euro, al cambio del suo giorno: la base di ogni totale.
    -- Sommare dollari ed euro come se fossero la stessa cosa darebbe un numero che
    -- non vuol dire niente. Per un movimento in euro coincide con `amount_cents`.
    converted_amount_cents bigint     not null,

    -- L'istante come lo dà la sorgente, in UTC. Il giorno non si memorizza: dipende
    -- dal fuso di chi guarda, e lo ricavano le query da quello del profilo.
    recorded_at          timestamptz  not null,

    -- Verso e stato normalizzati di Margine ('wait-for-assign').
    direction            varchar(20)  not null,
    state                varchar(20)  not null,

    -- `text` e non un varchar con un limite inventato: sono testo libero scritto
    -- da una persona in un'altra applicazione, e troncarlo perderebbe dato per
    -- rispettare un numero che non ha nessuna ragione dietro.
    --
    -- Entrambi li scrive l'import alla prima scrittura e poi sono dell'utente.
    -- Quello che arriva dalla sorgente, misurato: `description` c'è su 1424
    -- movimenti di 1678 (85%) ma spesso è il tracciato che la banca ha generato
    -- ('PAGAMENTO DEBINT PAGAMENTO CARTA DI DEBITO INTERNAZIONALE 49...'), e
    -- `counter_party` c'è su 1082 (64%) con 429 valori distinti ma, quando c'è, è
    -- esattamente l'informazione giusta: Amazon, Conad, PayPal EUR, nomi di
    -- persone. Sono i due campi che l'utente riscrive, ed è per questo che
    -- nessun import successivo li ritocca.
    description          text,
    counter_party        text,

    -- La categoria del movimento, risolta nell'identificatore di Margine.
    -- L'assegna l'import alla prima scrittura, poi è dell'utente.
    --
    -- `on delete set null`: unire una categoria in un'altra sposta prima i suoi
    -- movimenti, ma se una sparisse comunque, un movimento senza categoria è un
    -- movimento da riclassificare, non un movimento da perdere.
    category_id          uuid         references categories (id) on delete set null,

    -- La categoria come la sorgente la dichiara, tenuta grezza. Non è un ripiego
    -- di visualizzazione — nessuno la mostra — ma la traccia con cui l'import
    -- riaggancia un movimento attraverso `source_categories` anche a distanza di
    -- import, e l'unico modo di riconoscerne la categoria se quella riga si
    -- perdesse.
    source_category_id   varchar(120),
    source_category_name varchar(200),

    -- Giroconto: `transfer_state` dice che il movimento è una gamba di un
    -- trasferimento, `transfer_external_id` la lega all'altra gamba. Sono due
    -- colonne e non una perché nei dati reali capita che la gamba sia dichiarata
    -- senza identificativo (14 movimenti su 1672): dichiarata ma non accoppiabile.
    transfer_state       varchar(20),
    transfer_external_id varchar(120),

    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),

    -- L'idempotenza dell'import, garantita dal database e non dal codice.
    unique (user_id, source, external_id)
);

-- L'indice su cui poggia ogni domanda che il prodotto fa davvero: quanto ho
-- speso in questo periodo. Un periodo nel fuso dell'utente diventa un intervallo
-- di istanti, quindi resta una scansione di intervallo; `include` porta nella
-- foglia le colonne che servono a sommare, così la finestra si risolve senza
-- tornare sulla tabella. Misurato su 4,2M di righe con la stessa forma sul giorno
-- invece che sull'istante: ~0,1 ms per un mese, e non cresce con lo storico.
create index movements_per_periodo
    on movements (user_id, recorded_at) include (account_id, converted_amount_cents);

-- Il conto di un movimento si chiede anche al contrario: "i movimenti di questo
-- conto". Senza indice sarebbe una scansione, e la foreign key da sola non ne crea uno.
create index movements_per_conto on movements (account_id);

-- «Quanto ho speso in questa categoria»: la categoria che conta è la colonna, e
-- un indice sulla colonna lo dice senza intermediari.
create index movements_per_categoria on movements (user_id, category_id);

-- Il riaggancio delle categorie, e perché ha un indice tutto suo.
--
-- Un import è incrementale: riparte dal segnaposto. Quindi non basta a riempire
-- `category_id` sullo storico — misurato: il primo import dopo aver aggiunto la
-- colonna riagganciò **1 movimento su 1678**, e gli altri 1677 sarebbero rimasti
-- senza categoria risolta fino al giorno in cui la sorgente avesse toccato quella riga.
--
-- La risposta è un passo di riparazione dentro l'import: dopo aver allineato le
-- categorie, una sola `update ... from source_categories` aggancia tutti i movimenti che
-- hanno la traccia della sorgente e non ancora il riferimento risolto. È
-- idempotente, si spegne da sola, e vale anche per il caso che si ripresenterà —
-- una categoria creata nella sorgente dopo i movimenti che ci stanno dentro.
--
-- Quel passo gira a ogni import, quindi la sua condizione va resa gratuita quando
-- non c'è niente da fare, che è il caso normale. Un indice parziale sui soli
-- movimenti non agganciati è esattamente questo: di norma è vuoto, occupa pagine
-- zero, e la `update` si risolve senza scorrere la tabella.
create index movements_da_riagganciare
    on movements (user_id, source)
    where category_id is null and source_category_id is not null;

comment on column movements.external_id is
    'Identificativo del movimento nella sorgente: è ciò che rende ripetibile l''import.';
comment on column movements.amount_cents is
    'Importo con segno in centesimi interi. Il saldo di un conto è la somma di questi, mai una colonna.';
comment on column movements.converted_amount_cents is
    'L''importo in euro al cambio del giorno del movimento: la base di ogni totale. Uguale ad amount_cents per i movimenti in euro.';
comment on column movements.recorded_at is
    'L''istante del movimento come lo dà la sorgente, in UTC. Il giorno si ricava nel fuso del profilo.';
comment on column movements.description is
    'La descrizione del movimento: la scrive l''import quando il movimento entra, '
    'poi è dell''utente. Null significa «non c''è», come ovunque.';
comment on column movements.counter_party is
    'Pagatore o pagante: la scrive l''import quando il movimento entra, poi è dell''utente.';
comment on column movements.category_id is
    'La categoria del movimento. La assegna l''import alla prima scrittura, '
    'poi nessun import la tocca più.';
comment on column movements.source_category_name is
    'Categoria come la chiama la sorgente: traccia grezza per il riaggancio, non si mostra.';
comment on index movements_da_riagganciare is
    'Solo i movimenti con la traccia della categoria ma senza riferimento risolto: '
    'di norma vuoto, serve al passo di riaggancio dell''import.';


-- ---------------------------------------------------------------------------
-- budgets — limiti mensili di spesa su gruppi di categorie (`core/budgets`).
--
-- Due livelli: un budget principale raccoglie una o più categorie, un sotto-budget
-- ne prende un sottoinsieme con un tetto più stretto («Cibo», e dentro «Ristoranti»).
-- Le regole che tengono onesti i totali le fa rispettare lo schema, non il codice:
--
-- * una categoria sta in un solo budget principale, quindi la somma dei principali
--   non conta due volte la stessa spesa;
-- * un sotto-budget prende solo categorie del suo principale, e i sotto-budget
--   non se le dividono. Ne segue che anche fra sotto-budget di principali diversi
--   una categoria compare una volta sola, e che un terzo livello non può esistere:
--   le sue categorie urterebbero quelle del sotto-budget che le contiene.
-- ---------------------------------------------------------------------------
create table budgets (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    -- null per un budget principale. Cancellare il principale porta via i suoi sotto-budget.
    parent_id  uuid        references budgets (id) on delete cascade,
    name       varchar(80) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    -- Bersagli delle foreign key composte di budget_categories.
    unique (id, user_id),
    unique (id, parent_id)
);

create index budgets_per_utente on budgets (user_id);

create table budget_categories (
    budget_id        uuid not null,
    user_id          uuid not null,
    -- Senza cascata: togliere una categoria che sta in un budget deve fallire, non
    -- lasciare il budget con una parte di spesa in meno.
    category_id      uuid not null references categories (id),
    -- Il principale del budget, ripetuto qui perché i vincoli qui sotto possano vederlo.
    parent_budget_id uuid,
    primary key (budget_id, category_id),
    foreign key (budget_id, user_id) references budgets (id, user_id) on delete cascade,
    foreign key (budget_id, parent_budget_id) references budgets (id, parent_id),
    -- Il sotto-budget prende solo categorie del principale: toglierne una al
    -- principale mentre un sotto-budget la usa fallisce qui.
    foreign key (parent_budget_id, category_id) references budget_categories (budget_id, category_id)
);

create unique index budget_categories_una_per_principale
    on budget_categories (user_id, category_id) where parent_budget_id is null;
create unique index budget_categories_una_per_sotto_budget
    on budget_categories (user_id, category_id) where parent_budget_id is not null;

-- Il limite vale dal mese in cui è stato fissato fino al successivo: cambiarlo a
-- novembre non riscrive ottobre, che resta confrontato col tetto che aveva.
create table budget_limits (
    budget_id    uuid   not null references budgets (id) on delete cascade,
    valid_from   date   not null check (extract(day from valid_from) = 1),
    amount_cents bigint not null check (amount_cents > 0),
    primary key (budget_id, valid_from)
);

comment on column budget_limits.valid_from is
    'Primo giorno del mese da cui il limite vale; resta in vigore fino alla riga successiva.';
