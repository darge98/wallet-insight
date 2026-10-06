# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Contesto

`backend/` è uno dei due progetti del monorepo **Wallet Insights**: `frontend/` (Angular 22 + Nx)
e `backend/` (questo, Spring Boot + Gradle), che include anche l'importazione dalle
sorgenti esterne. Ognuno ha il proprio build system e si sviluppa in isolamento; `compose.yaml`
alla radice avvia lo stack completo (Postgres :5432, backend :8080, frontend :8081) leggendo
`.env` (template in `.env.example`).

Stack: Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring Data JDBC (niente JPA), Flyway,
PostgreSQL 17. Java 25 richiesto: il toolchain lo scarica Gradle da solo (foojay resolver).

**Stato del progetto**: l'infrastruttura è pronta e verificata (modularità, errori, formato
risposte, testcontainers). I moduli di dominio sono sette:

- `core/users` — anagrafica e impostazioni su `/api/users`.
- `core/ingestion` — le sorgenti collegate da un utente su
  `/api/users/{id}/import-connections`, con il segnaposto dell'import incrementale
  (`last_record_date`) e l'istante dell'ultima esecuzione riuscita (`last_run_at` —
  cosa diversa dal segnaposto: un giro a vuoto muove questo e non quello), e
  **`ImportService`**, il caso
  d'uso che porta i dati dentro. Gli adapter verso le sorgenti esterne stanno in
  `infrastructure/budgetbakers` e sono privati al modulo: il dominio ne conosce solo la
  porta `ImportSource`.
- `core/accounts` — i conti importati, primo pezzo del dominio
  finanziario. Esposti su `/api/users/{id}/accounts`: elenco (col saldo) e rinomina,
  niente altro. Un conto lo crea l'import (la sua identità comprende `source` +
  `external_id`) e non si cancella, perché possiede movimenti storici. Nome e colore
  sono dell'utente: nessun import li tocca, e il `PATCH` è
  parziale — un campo assente vale «non toccare», non «azzera».
- `core/categories` — le categorie **di Wallet Insights**: un albero a due livelli (macro →
  sottocategoria; i movimenti stanno solo nelle sottocategorie) che parte dall'elenco di
  base (`DefaultCategories`, 11 macro e 60 sottocategorie, copiato per utente al primo
  accesso o al primo import) e che l'utente rinomina, sposta, crea e unisce. Le categorie
  delle sorgenti stanno in `source_categories`, ognuna **agganciata** a una di Wallet Insights:
  l'import classifica attraverso l'aggancio, e riagganciare sposta anche i movimenti già
  importati rimasti dove l'aggancio li aveva messi. Il primo aggancio lo decide
  `BudgetBakersCategories`: gli id delle categorie standard di BudgetBakers sono
  deterministici (`5c5c03e8-000a-…` = Spesa), le personali seguono quella da cui derivano
  (`parentId`), il resto ripiega sul gruppo e poi su «Da classificare». Unire una
  sottocategoria (`DELETE …/{id}?into=`) ne sposta movimenti e agganci, ma non è ammesso
  se sta in un budget. Verificato il 04/10/2026 sui dati reali: 1707 movimenti su 1707
  classificati, uscite totali invariate, ogni categoria con esattamente i movimenti delle
  categorie BudgetBakers agganciate. API su `/api/users/{id}/categories` (+ `/sources`).
- `core/movements` — i movimenti importati, esposti su
  `/api/users/{id}/movements`: elenco paginato con filtri (periodo, natura, conti,
  categorie — più valori dello stesso filtro sono in **or**), ordinamento (data, importo
  per valore assoluto, controparte mostrata), **totali dell'intero filtro** accanto alla
  pagina, e modifica parziale. Le aggregazioni di periodo stanno su
  `/api/users/{id}/analytics`: KPI con le tendenze, curva cumulativa delle uscite, uscite
  per categoria e per controparte. Nessuna ricerca testuale: è rimandata di proposito.
  Scrive con un `on conflict ... do update ... where ... is distinct from`, non con
  leggi-e-decidi: un import ne porta migliaia, e una lettura per riga sarebbero
  migliaia di viaggi. **Tre campi però l'import li scrive una
  volta sola**: descrizione, pagatore/pagante e categoria. Stanno
  fra i campi inseriti e non fra quelli aggiornati, quindi nessun giro successivo può
  calpestarli, e restano fuori anche dal confronto `is distinct from`. Il saldo di un
  conto è la somma dei movimenti, composta dal BFF.
- `core/budgets` — limiti mensili di spesa su gruppi di categorie, scritti dall'utente
  (nessun `source`), su `/api/users/{id}/budgets`: CRUD e `GET .../budgets/month?month=yyyy-MM`,
  il resoconto del mese composto dal BFF con la spesa di `core.movements`. **Due livelli**:
  un sotto-budget prende parte delle categorie del principale; la sua spesa sta già dentro
  quella del principale, e i totali del mese sommano solo i principali più il «fuori
  budget». Le regole fra budget le fa rispettare lo schema: una categoria in un solo
  principale e in un solo sotto-budget (indici unici parziali), sotto-budget ⊆ principale
  (foreign key composta), quindi niente terzo livello. `BudgetPlan` le ripete per dare un
  409 leggibile, e aggiunge l'unica che lo schema non vede: i limiti dei sotto-budget non
  superano quello del principale. **Il limite è storicizzato** (`budget_limits.valid_from`):
  un limite nuovo vale dal mese corrente nel fuso dell'utente, i mesi passati restano col
  loro. Le categorie invece no: aggiungerne una cambia anche i mesi passati. Verificato il
  04/10/2026 sui dati reali di settembre: speso per budget e uscite totali (1829,20)
  coincidono al centesimo con l'SQL diretto. BudgetBakers espone `/v1/api/budgets` con una
  forma compatibile (più categorie, `limitOverrides` dal mese in poi), ma non si importa:
  i budget nascono qui.
- `core/subscriptions` — gli abbonamenti, scritti dall'utente (nessun `source`), su
  `/api/users/{id}/subscriptions`: elenco, `POST`, `PUT` (non `PATCH`: togliere la data
  di fine deve potersi dire, e un campo assente e un `null` arrivano identici), `DELETE`
  e `GET .../subscriptions/overview?days=` (1–366), costo mensile e annuo degli attivi più
  gli addebiti da oggi a `oggi + days - 1`, e `GET .../subscriptions/calendar?month=yyyy-MM`,
  gli addebiti di un mese qualsiasi (i disdetti fino alla loro fine). Categoria (solo
  sottocategorie) e conto di addebito sono facoltativi, con foreign key composte con
  `user_id` (V3); unire una categoria sposta anche gli abbonamenti, in
  `bff/categories/CategoryReorganization` insieme ai movimenti. Cadenza libera «ogni N settimane/mesi/anni»
  dal primo addebito (`start_date`), ogni addebito contato da lì e non dal precedente
  (31/1 → 28/2 → 31/3); `end_date` facoltativa è la disdetta. Prossimo addebito e costi
  equivalenti dipendono da «oggi» nel fuso dell'utente, quindi non si salvano. Nessun
  legame diretto coi movimenti, per ora: riconoscere gli addebiti nello storico è il passo
  dopo, e categoria e conto sono ciò su cui poggerà.

Il dominio finanziario è verificato contro la sorgente, non contro se stesso: i saldi
calcolati da Wallet Insights coincidono al centesimo con quelli che BudgetBakers calcola, su
tutti e sei i conti reali (1335207, 9200, 367, 72, 0, 0 centesimi su 1678 movimenti).
Rifare quella misura dopo ogni modifica al mapper: si fa con un import vero e un
confronto prima/dopo, non con un test.

Il dominio finanziario va ricostruito a partire dalle sorgenti reali di ingestion
(BudgetBakers, PSD2): **non** riderivarlo dai tipi del frontend, è l'errore della versione
precedente. Movimenti, categorie e budget seguiranno con lo stesso metodo.

**L'importazione gira dentro il backend**, non in un processo separato, ed è sincrona perché
a misura reale costa ~1,5 s in incrementale e ~3 s per l'import completo. La ragione di
fondo non è la comodità: il token della sorgente non deve uscire da dove vive la chiave che
lo decifra.

**Chi la fa partire non è più l'interfaccia.** Il primo import parte dall'onboarding:
`POST /api/onboarding` con una sorgente crea il profilo, collega la sorgente e poi
importa, così la panoramica si apre su movimenti veri invece che su una schermata vuota
con un pulsante "aggiorna". La logica è quella di sempre, quella che serviva il pulsante:
primo giro tutto lo storico (`BudgetBakersSource.DALL_ORIGINE`, l'epoch), dal secondo in
poi incrementale dal segnaposto. Costa i ~3 s misurati: **non accorciare quella finestra**,
e non farla diventare una proprietà di configurazione — c'era, ed è stata tolta: l'unico
valore che non rompe i saldi è "da sempre", quindi non c'è niente da configurare. Ometterla
non è un'opzione: senza filtro esplicito l'API ne applica uno suo di tre mesi a ritroso e
lo dichiara solo in `appliedRecordDateFilters`. Il saldo di un conto è il saldo iniziale dichiarato dalla sorgente più
la somma dei movimenti che abbiamo, quindi una finestra corta non lascia indietro righe
vecchie e innocue, sbaglia il saldo di oggi di ogni conto — provato il 24/09/2026 con tre
mesi: PayPal 4531,60 invece di 0, Credem 11987,13 invece di 13220, Crypto.com Card 109,37
invece di 0,72. Dal secondo giro in poi comanda il segnaposto, e ad aggiornare sono le
**schedulazioni, una per sorgente**: oggi `infrastructure/budgetbakers/BudgetBakersImport`
(5:00 Europe/Rome, `margine.budgetbakers.import.cron`), che chiama lo **stesso**
`ImportService.importForAllUsers(BUDGET_BAKERS)` e all'avvio importa ogni connessione il
cui ultimo import riuscito (`last_run_at`) ha almeno 15 minuti: la soglia protegge il limite
della sorgente (300 richieste/ora per token) da un container che riparte in ciclo. Un 429
diventa `SourceUnavailableException` col tempo di `Retry-After`, senza nuovi tentativi. Un 401 della sorgente diventa
`RejectedCredentialsException` e segna `credentials_rejected_at` sulla connessione;
`PUT .../import-connections/{source}` con un token nuovo lo azzera e importa subito. Nei test è spenta
(`margine.budgetbakers.import.cron=-` in `AbstractDatabaseTest`). `POST /api/users/{id}/imports` resta, ma come import chiesto a mano —
assistenza, misure, il giro che la notte non ha fatto — non come pulsante di una schermata.

Nell'onboarding i confini transazionali sono **due**: le due scritture stanno in
`OnboardingRegistration` (`@Transactional`, un token rifiutato non lascia un utente a
metà), l'import gira **dopo** il commit, in `OnboardingService`, che è un bean diverso
proprio per questo — un metodo `@Transactional` chiamato dall'interno non aprirebbe nessun
confine. Un import fallito non fa fallire il primo accesso: il profilo esiste, i dati
arrivano col giro dopo. `README.md` è la documentazione di riferimento.

## Comandi

Sempre da `backend/`:

```bash
./gradlew test                                          # tutti i test, confini fra moduli compresi
./gradlew test --tests 'it.walletinsight.ModularityTests'     # solo la verifica dei confini
./gradlew test --tests 'it.walletinsight.shared.money.MoneyTest'   # singola classe
docker compose up -d --build backend                    # dalla radice: stack con il codice nuovo
curl -X POST localhost:8080/api/users/{id}/imports      # l'import vero, la misura che conta
ENCRYPTION_KEY=$(openssl rand -base64 32) \
  ./gradlew bootRun --args='--spring.profiles.active=local'  # API su :8080, CORS verso :4200
./gradlew build                                         # compila e produce il jar
```

- **Docker serve** per i test che toccano il DB: usano Testcontainers (`postgres:17-alpine`,
  collegato al datasource via `@ServiceConnection` in `TestcontainersConfiguration`). I test
  DB estendono `support/AbstractDatabaseTest`. Test unitari e di modularità girano anche
  senza Docker.
- Per `bootRun` in locale serve Postgres: `docker compose up -d` da `backend/` (il
  `compose.yaml` qui dentro avvia solo Postgres; è indipendente da quello alla radice).
- Nessun linter né formatter configurati: i confini fra moduli sono garantiti da un test,
  non da checkstyle/spotless.
- `./gradlew test` genera anche i diagrammi PlantUML e i canvas in `build/spring-modulith-docs`.

## Architettura (monolite modulare)

I moduli Spring Modulith sono i package annotati con `@ApplicationModule` a qualsiasi
profondità (detection strategy `explicitly-annotated` in `application.yaml`): `shared` e
`platform` (entrambi `Type.OPEN`, condivisi via `@Modulithic`) stanno direttamente sotto
`it.walletinsight`, accanto a `bff`; i moduli di dominio stanno sotto il namespace `core/` (oggi
`core.users`, `core.ingestion`, `core.accounts`, `core.categories`, `core.movements`, `core.budgets` e
`core.subscriptions`; fuori da `it.walletinsight` c'è solo `db.migration`, le migrazioni Java). Un package senza annotazione **non** è
un modulo: ogni
nuovo modulo deve annotare il proprio `package-info.java`.

Il **frontend è collegato per davvero**: movimenti, categorie, conti, classifiche e KPI
arrivano da qui, budget compresi: nel frontend non resta nessun adapter dimostrativo.

**`bff` è l'unico layer HTTP.** I moduli di dominio non hanno controller: sono esagonali a
tre layer (`domain/`, `application/`, `infrastructure/`) e si fermano al proprio caso d'uso.
Controller e DTO vivono in `bff/<area>/`, che chiama i servizi dei moduli e **compone** ciò
che una schermata chiede in una chiamata sola (`bff/onboarding`: profilo e sorgente insieme,
in una transazione). Un nuovo endpoint si aggiunge lì, mai dentro `core/`.

- Ogni modulo di dominio ha **due aperture pubbliche**: `domain/` (`@NamedInterface("domain")`,
  i tipi — è ciò che un altro modulo di dominio può importare) e `application/`
  (`@NamedInterface("application")`, i servizi — li chiama il BFF). `infrastructure/` resta
  privata sempre. Le dipendenze si dichiarano con `@ApplicationModule(allowedDependencies)`
  (nomi logici `core.users`, `core.ingestion`, `bff`; sintassi `core.users::domain`).
- Violare un confine fa fallire `ModularityTests` (`ApplicationModules.verify()`).
- I percorsi HTTP sono in `platform/web/ApiPaths`: c'è solo `/api`. Il CORS copre
  `GET/POST/PUT/PATCH/DELETE` su `/api/**`, con le origini in `margine.allowed-origins`
  (il profilo `local` aggiunge `http://localhost:4200`). Non esiste più un `/internal`:
  serviva a consegnare i token in chiaro a un processo di ingestion separato, e da quando
  l'importazione gira qui dentro il token non lascia più il processo che lo decifra.

## Convenzioni vincolanti (contratto HTTP col frontend)

- **Identificatori UUIDv7**: li genera l'applicazione (`Uuids.v7()` in `shared/identifier`),
  mai ID tecnici del database — stabili fra ambienti e sequenziali nel tempo, così gli indici
  B-tree restano compatti. Il tipo del dominio (es. `UserId`) avvolge `java.util.UUID`.
- **Importi interi in centesimi**: `Money` è `long`, mai `BigDecimal`, nemmeno nel JSON —
  il frontend somma in interi per evitare il floating point.
- **I totali sono in euro**: ogni movimento porta l'importo nella valuta del conto
  (`amount_cents`, su cui si calcola il saldo del conto) e quello in euro al cambio del suo
  giorno (`converted_amount_cents`), che l'import chiede a BudgetBakers con
  `convertTo=EUR`. KPI, classifiche, curva, budget, totali dell'elenco e patrimonio sommano
  il secondo; il saldo iniziale di un conto in valuta si converte col cambio del suo primo
  movimento. Un movimento in valuta senza cambio fa fallire l'import invece di entrare.
- **Date senza fuso nel contratto**: `LocalDate`, serializzata `yyyy-MM-dd`.
- **Un movimento è un istante, non un giorno** (`recorded_at timestamptz`): l'istante
  della sorgente in UTC. Il giorno lo decide il fuso del profilo utente: i filtri di periodo
  diventano intervalli di istanti (`MovementService` passa il fuso al repository), la curva
  raggruppa con `at time zone`, il BFF scrive la data con `Movement.dateIn`. Il segnaposto e
  la finestra dell'import restano invece in giorni UTC, la lingua dei filtri di BudgetBakers.
  Limite noto: i movimenti di cui la sorgente sa solo il giorno arrivano a mezzanotte UTC, e
  in un fuso a ovest di Greenwich cadrebbero il giorno prima.
- **Enum in kebab-case** nel JSON (`credit-card`), tradotte esplicitamente da
  `platform/web/KebabCase` nei mapper DTO, non da una configurazione globale di Jackson.
- **`Page<T>` è nostro** (`shared/page/Page.java`): `{items, total, index, size, pageCount}`.
  Mai usare `org.springframework.data.domain.Page`: serializza `content`/`totalElements` e
  rompe il contratto.
- **Errori come `ProblemDetail`** (RFC 9457), gestiti in `platform/web/ApiExceptionHandler`.
- **Le aggregazioni le fa PostgreSQL** e l'SQL vive solo in `infrastructure/jdbc` del modulo:
  cambiare database tocca solo quel layer.
- **I saldi non si memorizzano, si calcolano.** `accounts` porta solo
  `initial_balance_cents`; il saldo corrente è la somma dei movimenti a partire da lì.
  Misurato su 4,2M di righe: una finestra di periodo (mese, anno) costa ~0,1 ms con
  l'indice `(user_id, date) include (account_id, amount_cents)` e **non cresce** con lo
  storico (misura fatta quando l'indice stava sul giorno; ora sta su `recorded_at`, con la
  stessa forma: da rifare); solo la somma non limitata del saldo corrente cresce (2 ms su 10 anni, 42 ms su
  500k movimenti). Se un giorno darà fastidio, la risposta è un saldo di chiusura mensile,
  non una colonna con l'ultimo saldo noto: quella avrebbe due verità in disaccordo.
- **I dati importati conservano il riferimento all'originale**: `source` + `external_id`
  identificano il dato nella sorgente e non cambiano mai. È ciò che rende un import
  idempotente e ciò che permette all'utente di rinominare senza perdere l'aggancio — in
  `accounts`, `name` è dell'utente e `source_name` della sorgente: finché coincidono il
  nome segue la sorgente, appena divergono un import non tocca più `name`.
- **I segreti si cifrano**: le credenziali delle sorgenti di importazione (token, API key,
  password) non toccano mai il database in chiaro. Cifra e decifra
  `core/ingestion/infrastructure/crypto/SecretCipher` (AES-256-GCM) dentro il layer
  `infrastructure`, mai nel dominio; la chiave sta in `ENCRYPTION_KEY` (32 byte
  base64) e senza di essa il contesto non parte, test compresi. Verso l'API esce solo la
  traccia del segreto (`…a1b2`), mai il valore.
- **Ciò che è dell'utente convive con ciò che è della sorgente**, e ci sono due regole,
  non una. Sui *nomi* (conto, categoria) i due valori si confrontano: finché coincidono
  il nome segue la sorgente, appena divergono l'import non lo tocca più — e riscriverlo
  identico alla sorgente è il modo di tornare a seguirla. Sul *colore* vale invece
  `null` = «non ha scelto, segui la sorgente», perché un colore può mancare da entrambe
  le parti e un confronto non saprebbe distinguere «non ho scelto» da «ho scelto lo
  stesso».
- **Sui tre campi di un movimento non vale nessuna delle due**, e la storia serve a non
  rifarla. V9 aveva dato a descrizione, pagatore/pagante e categoria due colonne ciascuno
  con `null` = «segui la sorgente»: funzionava, ma rendeva impossibile svuotare un campo,
  perché `null` era già occupato. Dare un significato alla stringa vuota ha spostato il
  problema (niente poteva più riportare la colonna a `null`). **V11 tiene un campo solo**:
  l'import ci scrive quando il movimento entra, poi non lo tocca più, non c'è ripiego da
  calcolare in lettura e `null` torna a voler dire «vuoto» come ovunque.
  `source_category_id`/`source_category_name` restano, ma non sono un ripiego di
  visualizzazione: sono la traccia con cui l'import riaggancia un movimento alla sua riga
  in `categories` (V10).
- **Nel PATCH di un movimento un campo presente è il nuovo valore, stringa vuota
  compresa**; solo l'assenza vuol dire «non toccare» — misurato, con Jackson 3 e i record
  un campo assente e un `null` esplicito arrivano identici (entrambi `Optional.empty`),
  quindi l'assenza è l'unico significato che resta libero. Svuotare un campo lo lascia
  vuoto, davvero: non c'è più nessuna seconda colonna pronta a riprendersi la scena.
- Prezzo della semplificazione, da conoscere: **riscrivere una descrizione cancella quella
  con cui il movimento era arrivato**, e l'import non la riporterà. Il valore originale
  esiste solo nella sorgente esterna.
- **Un movimento `UNCLEARED` non si corregge** (`Movement.editable()`, 409 dal PATCH), ed è
  una regola misurata, non prudenza: quando la banca conferma una transazione in sospeso,
  BudgetBakers **non aggiorna il record — ne crea un altro, con un identificativo nuovo, e
  fa sparire quello in sospeso**. Verificato leggendo la finestra: il record nuovo risulta
  creato dalla sorgente ore prima di qualsiasi nostra lettura, e del vecchio non c'è più
  traccia. Scrivere su un movimento in sospeso vorrebbe dire scrivere su una riga destinata
  a sparire.
- **I movimenti in sospeso sostituiti si cancellano.** Dal secondo import la finestra
  parte dalla data più vecchia fra il segnaposto e **oggi meno 3 mesi**
  (`BudgetBakersSource.RILETTURA`): la finestra si allarga, non si accorcia — il primo
  import resta dalle origini. Nella stessa transazione del salvataggio
  (`MovementService.saveImportedWindow`) si cancellano i movimenti `uncleared` che stanno
  nella finestra, su un conto restituito in quel giro, e che la sorgente non restituisce
  più; ognuno finisce nel log. Se BudgetBakers dichiara in `appliedRecordDateFilters` una
  finestra più stretta di quella chiesta, l'import fallisce invece di cancellare.
  Scelte misurate il 04/10/2026: con i 3 doppioni del 22/09 settembre segnava 1871,80 di
  uscite contro 1829,20 della sorgente; dopo la cancellazione coincidono uscite, entrate,
  i saldi di tutti e sei i conti e il numero di movimenti (1707). Scartati: marcare le righe
  invece di cancellarle (ogni query di lettura dovrebbe ricordarsi il filtro) e abbinare
  in sospeso e confermato (la descrizione differisce 1 volta su 3, l'importo cambia con le
  preautorizzazioni). Tre mesi di rilettura sono 178 movimenti, una pagina; in cambio una
  riga cancellata per errore torna al giro dopo se la sorgente la restituisce ancora.
- Migrazioni Flyway in `src/main/resources/db/migration/`. `V1__schema_iniziale.sql`
  crea tutto lo schema nella forma attuale (il 05/10/2026 vi sono confluite le
  migrazioni precedenti, su un database rifatto da zero); da lì si aggiunge (`V2__...`),
  e **una migrazione già applicata non si modifica**, nemmeno nei commenti: Flyway ne
  verifica il checksum e il contesto non parte più (serve un repair sullo storico, o un
  database rifatto da zero). Niente migrazioni Java che usano classi dell'applicazione:
  quelle classi cambiano, e una migrazione deve fare per sempre la stessa cosa.
- Codice, commenti e documentazione **in italiano**; i commenti spiegano il *perché*. Anche i
  nomi dei metodi di test sono in italiano.
