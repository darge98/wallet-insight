# Wallet Insights — API

Backend del financial tracker [Wallet Insights](../frontend).

## Stato del progetto

L'infrastruttura è pronta e verificata: modularità, configurazione, gestione
degli errori, formato delle risposte, container di test. Il dominio finanziario
si sta ricostruendo a partire dalle sorgenti reali (BudgetBakers, PSD2, ...) e
non dai tipi del frontend, che era l'errore della versione precedente.

I moduli di dominio sono sei: `core/users` (anagrafica e impostazioni),
`core/ingestion` (le sorgenti collegate da ogni utente, le loro credenziali
cifrate a riposo e l'import), `core/accounts` (i conti importati),
`core/categories` (la classificazione), `core/movements` (i movimenti, il dato
per cui il resto esiste) e `core/budgets` (i limiti mensili di spesa). Nessuno espone HTTP: gli endpoint vivono in `bff`, il
layer che parla col frontend e compone i casi d'uso — `POST /api/onboarding`
crea il profilo e collega la sorgente in una chiamata e in una transazione.


## Requisiti

| Strumento | Versione |
| --------- | -------- |
| JDK       | 25 (Gradle lo scarica da solo) |
| Docker    | per PostgreSQL e i test |

## Avvio rapido

```bash
docker compose up -d
export ENCRYPTION_KEY=$(openssl rand -base64 32)   # una volta, poi tienila
./gradlew bootRun --args='--spring.profiles.active=local'
```

Senza `ENCRYPTION_KEY` l'applicazione non parte: è la chiave con cui
cifra le credenziali delle sorgenti di importazione (vedi [Segreti](#segreti)).

L'API risponde su `http://localhost:8080`. Il profilo `local` abilita anche
il CORS verso il dev server Angular su `http://localhost:4200`.

La documentazione OpenAPI è su `http://localhost:8080/v3/api-docs`, con
interfaccia web su `http://localhost:8080/swagger-ui.html`.

## Schema

`src/main/resources/db/migration/V1__schema_iniziale.sql` crea tutto lo schema, già
nella forma attuale: la storia di come ci si è arrivati non la vive nessun database
creato oggi. Da lì in poi si aggiunge (`V2__...`), e una migrazione applicata non si
modifica.

Nove tabelle, nell'ordine delle dipendenze. `users` è l'anagrafica di `core/users`,
con le impostazioni (fuso, lingua, periodo predefinito). `import_connections` tiene
una riga per coppia (utente, sorgente) con il segreto cifrato e tre istanti che
rispondono a domande diverse: `last_record_date`, il segnaposto da cui riparte
l'import incrementale (la data del *dato* più recente letto, non dell'esecuzione: un
giro a vuoto non lo sposta, e un movimento registrato in ritardo non si perde);
`last_run_at`, quando si è guardato, che risponde a «ho i dati aggiornati?»;
`credentials_rejected_at`, quando la sorgente ha rifiutato il token — lo scrive un
import che riceve 401, lo azzerano un import riuscito o un token nuovo.

`accounts` è una riga per conto importato, con il riferimento all'originale
(`source` + `external_id`) che non cambia mai e i due nomi di proprietà diversa
(`name` dell'utente, `source_name` della sorgente). Porta solo
`initial_balance_cents`: il saldo corrente è la somma dei movimenti e si calcola, non
si memorizza.

`categories` sono le categorie **di Wallet Insights**, un albero macro → sottocategoria che
parte dall'elenco di base (`template_key` ricorda da quale voce è nata una categoria).
`source_categories` sono quelle delle sorgenti, ognuna agganciata a una di Wallet Insights
(`category_id`): l'import classifica attraverso l'aggancio.

`movements` è il dato per cui tutto il resto esiste: importo con segno in centesimi
nella valuta del conto (`amount_cents`, su cui si calcola il saldo) e in euro al
cambio del suo giorno (`converted_amount_cents`, che sommano tutti i totali);
`recorded_at`, l'istante della sorgente in UTC, da cui le query ricavano il giorno nel
fuso del profilo; e lo stesso riferimento all'originale che rende ripetibile
l'import. Descrizione, pagatore/pagante e categoria hanno **una colonna sola
ciascuno**: le scrive l'import quando il movimento entra, e da lì in poi sono
dell'utente. L'indice `(user_id, recorded_at) include (account_id,
converted_amount_cents)` è quello su cui poggiano le domande di periodo, e un indice
parziale sui soli movimenti senza categoria risolta rende gratuito il passo di
riaggancio descritto più avanti.

`budgets`, `budget_categories` e `budget_limits` sono i limiti mensili di spesa: le
regole fra budget (una categoria in un solo principale e in un solo sotto-budget,
sotto-budget ⊆ principale) le fanno rispettare gli indici unici parziali e le foreign
key composte, e il limite è storicizzato per mese (`valid_from`).

## Segreti

Le credenziali che l'utente affida all'applicazione — oggi il token personale
di BudgetBakers, domani API key e password di altre sorgenti — non stanno in
chiaro nel database: `core/ingestion/infrastructure/crypto` le cifra con
AES-256-GCM prima dell'insert e le decifra rileggendole, così che un dump del
database non basti a usarle.

La chiave arriva dall'ambiente (`ENCRYPTION_KEY`, 32 byte in base64) e
non dal repository: ogni istanza genera la propria con `openssl rand -base64
32`, e comprometterne una non compromette le altre. Se manca, il contesto non
parte: meglio un'istanza che non si avvia di una che cifra con una chiave nota.

GCM autentica oltre a cifrare, quindi una riga modificata a mano non si decifra
in silenzio ma fallisce; il valore cifrato è legato alla coppia (utente,
sorgente) della sua riga, e copiarlo altrove lo rende inservibile. Il segreto
non torna mai indietro dall'API: di lui resta la sola traccia (`…a1b2`), quel
tanto che basta a riconoscerlo.

## Comandi

| Comando | Cosa fa |
| ------- | ------- |
| `./gradlew test` | Tutti i test, confini fra moduli compresi |
| `./gradlew test --tests 'it.walletinsight.ModularityTests'` | Solo la verifica dei confini |
| `./gradlew bootRun` | Avvia l'applicazione |
| `./gradlew build` | Compila e produce il jar |

## Mappa dei moduli

```
it.walletinsight/
├── shared/           Money, DateRange, Page, Uuids, IngestionSource  modulo condiviso
├── platform/         web, configurazione, errori              modulo condiviso
├── bff/              l'unico layer HTTP: controller, DTO, composizione
│   ├── users/        /api/users
│   ├── imports/      /api/users/{id}/import-connections e .../imports (import a mano)
│   ├── accounts/     /api/users/{id}/accounts — elenco (col saldo) e rinomina
│   ├── categories/   /api/users/{id}/categories — elenco, nome e colore
│   ├── movements/    /api/users/{id}/movements — elenco paginato e correzione
│   └── onboarding/   /api/onboarding — i due domini in una chiamata
└── core/             namespace dei moduli di dominio
    ├── users/        anagrafica e impostazioni
    ├── ingestion/    sorgenti collegate, credenziali cifrate e l'import
    ├── accounts/     i conti importati, con il riferimento all'originale
    ├── categories/   la classificazione: nata dall'import, rinominabile
    └── movements/    i movimenti importati: il saldo è la loro somma
```

Le dipendenze dichiarate sono poche e tutte giustificate: `core.ingestion` →
`core.users::domain`, `core.accounts` e `core.categories` (importare significa,
prima di tutto, creare i conti su cui i movimenti si appoggeranno e le categorie
che li classificano), `core.movements` → gli stessi tre domini, `core.accounts` e
`core.categories` → `core.users::domain`, e `bff` → tutti attraverso `::domain` e
`::application`. Tutto il resto è vietato e lo verifica un test.

`IngestionSource` vive in `shared/source` e non dentro `core/ingestion`: lo usano
sia le connessioni sia i conti — che ricordano da dove sono arrivati — e tenerlo
in uno dei due moduli creerebbe un ciclo fra loro, che `ModularityTests`
rifiuterebbe. Un tipo che due moduli si contendono è un tipo nel posto sbagliato.

I moduli di dominio (uno per bounded context) crescono sotto `core/` via via
che il modello viene ridisegnato: la mappa qui sopra si aggiornerà con loro.

## Forma di un modulo

Ogni modulo di dominio sotto `core/` è esagonale a tre layer:

```
<modulo>/
├── domain/          entità, value object, porte   ← apertura "domain"
├── application/     i servizi che orchestrano     ← apertura "application"
└── infrastructure/  gli adapter JDBC              ← privato
```

Le due aperture hanno due destinatari diversi: `domain/` è ciò che un altro
modulo di dominio può importare (l'equivalente del `src/index.ts` di una
libreria Nx nel frontend), `application/` è ciò che il BFF può chiamare.
`infrastructure/` non la importa nessuno: che una credenziale sia cifrata o che
una pagina arrivi da PostgreSQL resta un fatto interno al modulo.

Di HTTP nei moduli non c'è traccia. Un modulo di dominio non sa di essere
esposto sul web, e la stessa operazione può finire in più endpoint diversi
senza che il dominio se ne accorga: `POST /api/users` e `POST /api/onboarding`
chiamano lo stesso `UserService`.

## Il BFF

`bff/` è l'unico modulo con controller. Risponde sotto `/api` ed è organizzato
per area (`users/`, `imports/`, `accounts/`, `categories/`, `movements/`,
`onboarding/`), non per dominio: il suo compito
è dare al frontend la forma che gli serve, anche quando quella forma attraversa
due moduli.

`POST /api/onboarding` è il caso che giustifica il layer. Il primo accesso crea
un profilo e collega una sorgente: due scritture in due moduli, che il browser
dovrebbe altrimenti orchestrare con due chiamate, senza poter garantire nulla
se la seconda fallisce. Qui sono una transazione sola — o esistono entrambe, o
non esiste niente — e l'ordine (prima l'utente, poi la connessione, che a un
utente deve appoggiarsi) è deciso e verificato nel backend.

Il resto degli endpoint resta per risorsa (`/api/users`,
`/api/users/{id}/import-connections`): comporre serve dove c'è qualcosa da
comporre, e un CRUD non lo è. Che le connessioni siano annidate sotto `users`
nell'URL non dice di chi è il dato — il dominio è di `core/ingestion` — dice
come il frontend lo interroga.

`POST /api/onboarding` **importa**, oltre a creare: quando il corpo porta una
sorgente, il primo accesso collega la sorgente e poi ne importa subito i dati —
la stessa identica logica dell'import chiesto a mano, solo fatta partire da qui.
Il primo giro prende tutto lo storico e non una finestra recente: il saldo è il
saldo iniziale del conto più la somma dei movimenti importati, quindi accorciare
la finestra non lascia indietro righe vecchie e innocue, sbaglia il saldo di oggi
(misurato con tre mesi: PayPal 4531,60 invece di 0). La chiamata dura i ~3 s
misurati, ed è una scelta: l'alternativa
era entrare su una schermata vuota con un pulsante "aggiorna" da premere. Le due
scritture restano in transazione (`OnboardingRegistration`); l'import gira dopo
il commit, perché parla con una sorgente esterna e non deve tenere impegnata una
connessione al database per tutto quel tempo. Se non riesce, il primo accesso
riesce lo stesso: il profilo c'è e i dati arrivano col giro successivo.

`POST /api/users/{id}/imports` è l'import chiesto a mano — assistenza, misure, il
giro che la schedulazione non ha fatto. Non è più il pulsante di una schermata:
l'interfaccia non ha più un "aggiorna ora". La logica non sta qui ma in
`core/ingestion/application/ImportService`, e non per pignoleria — è lo stesso
caso d'uso che servono le schedulazioni delle sorgenti, e averlo in un posto solo è
tutto il punto.

Le schedulazioni sono **una per sorgente**: quali importare per un utente lo dicono
le sue connessioni. Oggi c'è `core/ingestion/infrastructure/budgetbakers/BudgetBakersImport`,
ogni giorno alle 5:00 Europe/Rome (`margine.budgetbakers.import.cron`, `-` la spegne),
su ogni connessione BudgetBakers attiva. All'avvio importa subito ogni connessione
mai importata o importata l'ultima volta (`last_run_at`) da almeno 15 minuti: chi
riaccende l'applicazione vuole i dati di adesso, e un incrementale costa tre chiamate.
La soglia c'è per il limite della sorgente, 300 richieste l'ora per token e condiviso
con l'MCP: un container che riparte in ciclo lo esaurirebbe, e BudgetBakers si riserva
di revocare il token a chi lo supera con insistenza. Un 429 diventa una sorgente non
disponibile che dice quanto aspettare (`Retry-After`), e non si ritenta. Gira su un'istanza sola: con più repliche servirà un lock.

Dal secondo import la finestra parte dalla data più vecchia fra il segnaposto e oggi
meno 3 mesi. I movimenti in sospeso che la sorgente non restituisce più — quando la banca
conferma, BudgetBakers crea un record nuovo e toglie il vecchio — si cancellano nella
stessa transazione del salvataggio, solo sui conti restituiti in quel giro; se la
sorgente applica una finestra più stretta di quella chiesta, l'import fallisce.

Un token scaduto o revocato (401 da BudgetBakers) diventa `RejectedCredentialsException`
e resta segnato sulla connessione (`credentialsRejectedAt` nella risposta), così
l'interfaccia può chiedere un token nuovo. `PUT .../import-connections/{source}` con il
token nuovo importa subito quella sorgente: la risposta dice già se il token funziona.

`GET /api/users/{id}/accounts` elenca i conti **con il saldo di oggi**, `PATCH
.../accounts/{accountId}` ne cambia il nome. Il saldo è il saldo iniziale del
conto più la somma dei suoi movimenti, e i due pezzi vengono da due moduli che non
si conoscono: comporli è il lavoro del BFF, e costa una sola aggregazione in più
per tutto l'utente. Nella risposta restano entrambi i numeri, perché la loro
differenza è quanto i movimenti importati dicono di aver spostato — il modo più
diretto di accorgersi che ne manca uno.

Del numero di conto escono solo le ultime quattro **cifre**. Il valore intero
resta nel database (servirà per un riconoscimento PSD2 o un export SEPA) ma non
viaggia: quello che la sorgente chiama `bankAccountNumber` è testo libero, e nei
dati reali contiene sia un numero di carta per intero sia stringhe come
`PayPal EUR`. Cifre e non caratteri, quindi: gli ultimi quattro caratteri di
`439772******6181 EUR` sarebbero ` EUR`. Sono le uniche due operazioni che esistono, e le mancanti
dicono più delle presenti: un conto non si crea a mano perché la sua identità
comprende il riferimento al dato originale (`source` + `externalId`) e uno
inventato dall'interfaccia non avrebbe nulla a cui agganciarsi; non si cancella
perché possiede movimenti storici, e il fatto che la sorgente oggi non lo elenchi
più non basta a distruggerli.

Nome e **colore** sono le uniche due cose che l'utente decide su un conto, e nessun
import le tocca. Il colore è nullable e senza default: `null` vuol dire «non ha
scelto», e assegnarne uno d'ufficio lo renderebbe indistinguibile da una scelta
vera. Arriva e riparte come `#rrggbb`, normalizzato minuscolo — `#FF8800` e
`#ff8800` sono lo stesso colore, e tenerli distinti farebbe risultare «cambiato»
un conto che nessuno ha toccato.

La rinomina regge su due colonne
invece che su un flag: `source_name` è come si chiama nella sorgente e viene
riscritto a ogni import, `name` è come si chiama in Wallet Insights. Finché coincidono il
nome segue la sorgente; appena divergono, un import non tocca più `name`. Il caso
limite è voluto — rinominare un conto esattamente come si chiama nella sorgente lo
rimette a seguirla, ed è il modo per annullare la rinomina senza un secondo
comando da inventare.

### I movimenti

`core/movements` scrive in un modo diverso dagli altri moduli, e la differenza ha
una ragione. I conti si rileggono prima di scrivere, perché hanno un campo che
appartiene all'utente e non va calpestato; i movimenti no — niente in un
movimento è dell'utente — e con qualche migliaio di righe per import una lettura
per riga sarebbe qualche migliaio di viaggi al database per scoprire quasi sempre
che non è cambiato nulla. Quindi un `insert ... on conflict (user_id, source,
external_id) do update ... where ... is distinct from ...`: il riconoscimento lo
fa il vincolo, e la `where` finale evita di riscrivere una riga identica. È ciò
che rende onesto `updated_at`, e ciò che permette di distinguere due numeri
nell'esito dell'import — quanti movimenti sono stati **letti** e quanti
**salvati**.

Misurato sui dati reali (1678 movimenti, sei conti): il primo import ne salva
1677 in ~2 s, il secondo giro completo ne legge 1678 e ne salva **0**. Ogni giro
rilegge di proposito il giorno già importato, quindi "letti 40, salvati 0" è
l'esito normale di un aggiornamento senza novità, e distinguerlo da "non ha
funzionato" è tutta la differenza per chi ha premuto il pulsante.

La verifica che conta non è che i numeri tornino fra loro, ma che tornino con la
sorgente: il saldo calcolato da Wallet Insights — saldo iniziale più somma dei movimenti —
coincide **al centesimo** con quello che BudgetBakers calcola per conto suo su
tutti e sei i conti (1335207, 9200, 367, 72, 0, 0 centesimi), e i conteggi per
conto con quelli che dichiara. È la prova che l'import non perde righe per strada,
e va rifatta ogni volta che si tocca il mapper.

Gli annullati (`state = 'void'`) sono fuori dalla somma: sono movimenti che non
sono avvenuti. Tutti gli altri stati sono denaro che si è mosso davvero, in un
momento diverso del suo percorso. I giroconti invece pesano — le due gambe si
annullano da sole sul totale — ma restano riconoscibili come tali, perché su
"quanto ho speso questo mese" contarli sarebbe un errore.

### Cosa è della sorgente e cosa di chi guarda

Fino a ieri in un movimento non c'era niente dell'utente, e quella frase reggeva
l'`upsert`: si riscrive tutto, sempre. Adesso tre cose sono sue — la descrizione,
il pagatore/pagante e la categoria — e la ragione è misurata sui 1678 movimenti
reali, non teorica:

| campo | copertura dalla sorgente | cosa contiene |
| ----- | ------------------------ | ------------- |
| `source_description` | 1424/1678 (85%) | spesso il tracciato della banca: `PAGAMENTO DEBINT PAGAMENTO CARTA DI DEBITO INTERNAZIONALE 497019******4104 -19/09/26-…` |
| `source_counter_party` | 1082/1678 (64%), 429 distinti | quando c'è, esattamente la cosa giusta: Amazon, Conad, Piadineria, PayPal EUR, nomi di persone |
| categoria | 1678/1678 (100%), 70 distinte | le categorie della sorgente, ora un dominio |

Una descrizione così non si legge, e su più di un terzo dei movimenti il pagatore
non arriverà mai. Quindi accanto al valore della sorgente ce n'è uno dell'utente,
e vince lui quando c'è.

La regola è quella del colore dei conti (`null` = «segui la sorgente»), **non**
quella dei due nomi: lì il confronto ha senso, perché riscrivere un conto
esattamente come lo chiama la sorgente è un modo naturale di dire «torna a
seguirla», mentre nessuno ricopierà mai a mano 168 caratteri di tracciato
bancario per annullare una modifica.

La conseguenza importante è che **l'import non cambia forma**: resta una sola
istruzione in batch, e le tre colonne dell'utente non le nomina proprio — non
servono `case` né letture preventive per proteggerle. Restano fuori anche dal
confronto `is distinct from`, così un movimento riscritto dall'utente ma non
cambiato alla sorgente risulta non toccato, come qualsiasi altro. Verificato su
dati reali: descrizione, pagatore e categoria modificati a mano, poi un import
vero — `letti 1, salvati 0`, e le tre modifiche ancora tutte lì.

Nel `PATCH`, «torna a seguire la sorgente» si dice con la **stringa vuota** e non
con `null`. Non è un gusto: misurato, con Jackson 3 e i record un campo assente e
un `null` esplicito arrivano identici (entrambi `Optional.empty`), quindi
l'assenza può significare soltanto «non toccare» e per l'operazione opposta serve
un valore. Dove la stringa vuota non è rappresentabile — la categoria — il caso
ha un tipo suo, `CategoryAssignment`, con tre nomi al posto di due parametri che
lascerebbero esprimibile la combinazione senza senso.

### Le categorie

Le categorie erano tenute grezze in due colonne dentro `movements`, in attesa di
sapere che forma dovessero avere. La misura l'ha detto: 70 categorie distinte su
1678 movimenti, legame identificativo–nome 1:1, copertura del 100%. È
un'anagrafica, non un'etichetta ripetuta 284 volte, e adesso è un modulo con la
stessa forma dei conti — riferimento all'originale che non cambia, due nomi di
proprietà diversa, nessuna creazione e nessuna cancellazione a mano.

Si leggono da `/v1/api/categories` e non raccogliendole dai movimenti, che pure
se le portano dietro denormalizzate. La differenza si vede negli import
incrementali, che guardano un giorno solo: vedrebbero due o tre categorie e una
rinomina fatta altrove non arriverebbe mai. La misura conferma anche altro —
l'endpoint ne restituisce **95**, contro le 70 che compaiono nei movimenti: 25
esistono senza essere ancora usate, e derivarle dai record le perderebbe.

Da lì arriva anche `source_group` (`food_and_drinks`), che il mapper prima
buttava via: è il solo segnale gerarchico che la sorgente regala, presente sul
100% delle 95 categorie, e le raggruppa in una dozzina di famiglie. Resta grezzo,
non normalizzato in un'enum di Wallet Insights: decidere una tassonomia con una sorgente
sola sotto gli occhi sarebbe deciderla senza i dati per farlo, e conservarlo è
ciò che permetterà di costruirla dai dati già importati.

`source_category_id` e `source_category_name` restano nei movimenti accanto al
riferimento risolto. Sembrano un doppione della riga in `categories` e servono a
due cose che quella riga non copre: riconoscere la classificazione di un
movimento se la categoria non fosse risolvibile, e **ricostruire l'aggancio senza
chiedere niente alla sorgente**.

Quest'ultima non è un'ipotesi. `V9` ha aggiunto `category_id` senza riempirlo,
contando sul primo import per farlo: sbagliato, e si è visto solo provandolo. Un
import è incrementale, quindi il primo dopo la migrazione ha riagganciato **1
movimento su 1678**. La risposta è un passo di riparazione dentro l'import — una
`update ... from categories` sulla traccia grezza, subito dopo l'allineamento
delle categorie — che ha sistemato i 1677 rimasti in un colpo, è idempotente, si
spegne da sola e vale anche per il caso che si ripresenterà: una categoria creata
nella sorgente dopo i movimenti che ci stanno dentro. Un indice parziale sui soli
movimenti non agganciati lo rende gratuito quando non c'è niente da fare, che è
il caso normale.

### Gli endpoint

`GET /api/users/{id}/movements` è paginato — i conti di una persona sono una
decina, i suoi movimenti sono 1678 dopo due anni — con filtri facoltativi per
periodo, natura (entrata, uscita, giroconto), conto e categoria. Criteri diversi
si combinano in **and**, più valori dello stesso criterio in **or**: due conti
selezionati vogliono dire «uno qualsiasi dei due», e intersecarli non darebbe mai
niente perché un movimento sta su un conto solo.

Le due date vanno insieme: una finestra con un solo estremo diventa un 400,
perché un elenco che mostra un periodo diverso da quello chiesto è peggio di un
errore. Un valore di filtro che non esiste è anch'esso un 400: rispondere «tutto»
a `?type=uscite` sembrerebbe funzionare, ed è il modo peggiore di scoprire un
errore di battitura.

Il filtro per categoria guarda quella **mostrata**, non quella della sorgente, ed
è l'espressione su cui è costruito l'indice: chiedere «i movimenti in Ristoranti»
e non vedere quello che ci si è appena spostato a mano sarebbe incomprensibile.

L'ordinamento (`sortBy`, `sortDirection`) arriva da un'enum e mai da una stringa
libera — un nome di colonna che viene da fuori è il modo classico di aprire
un'iniezione SQL. Per importo ordina sul **valore assoluto**: «i movimenti più
grandi» sono i più grandi, non tutte le entrate prima di tutte le uscite. L'id
chiude sempre l'ordinamento, altrimenti due righe pari sul criterio scelto
potrebbero scambiarsi di posto fra una pagina e l'altra, e un movimento
comparirebbe due volte o sparirebbe.

La risposta porta la pagina **e i totali dell'intero filtro**, da una chiamata
sola. Sono due interrogazioni sugli stessi criteri, e separarle vorrebbe dire due
risposte che raccontano due momenti diversi, con un riepilogo che non corrisponde
alle righe sotto di sé. Nelle somme i giroconti non entrano — le due gambe si
annullano e gonfierebbero insieme entrate e uscite — ma `count` li conta, perché
è lo stesso numero che la paginazione mostra lì sotto.

Non c'è una ricerca testuale, ed è una scelta rimandata: farla bene vuol dire un
indice apposta e decidere cosa significhi «trovare» — prefissi, accenti, parole in
mezzo — mentre farla come un `like '%…%'` costerebbe una scansione a ogni tasto
premuto per dare risultati che sembrano casuali.

### Le analisi di periodo

`/api/users/{id}/analytics` risponde a «quanto», non a «quali»: KPI del periodo
con le tendenze, curva cumulativa delle uscite, uscite per categoria e per
controparte. Sono aggregazioni e le fa PostgreSQL — il client non scarica lo
storico per sommarlo.

Due di questi giustificano il BFF. `kpi-summary` mette il patrimonio, che viene
dai conti, accanto ai totali di periodo, che vengono dai movimenti;
`expenses-by-category` mette gli importi, che conta `core/movements`, accanto ai
nomi, che ha `core/categories`. In entrambi i casi i moduli coinvolti non si
conoscono, e a comporli è questo layer.

Il periodo di confronto delle tendenze lo decide il server ed è «altrettanti
giorni prima», non «il mese scorso»: su «ultimi 7 giorni» il paragone giusto sono
i sette precedenti. Lasciarlo scegliere al client vorrebbe dire due definizioni
della stessa cosa che prima o poi divergono.

La quota di ogni categoria si calcola sul totale delle uscite di **tutto** il
periodo, non sulla somma delle categorie restituite: sulle prime cinque le
percentuali sommerebbero a cento e direbbero che lì è finito tutto. Verificato sui
dati reali di settembre: le cinque voci in classifica valgono il 77%, e il resto
sta nelle categorie che non entrano nell'elenco.

Chi non ha controparte resta fuori dalla classifica delle controparti invece di
finire in un gruppo «senza nome»: sarebbe quasi sempre il primo e non direbbe dove
sono andati i soldi.

`PATCH .../movements/{id}` cambia le tre cose dell'utente e nient'altro. Importo,
data, verso, stato e giroconto non compaiono nel corpo, e non è una svista: sono
fatti avvenuti in un'altra applicazione, e il prossimo import li riscriverebbe
comunque. Nella risposta ogni campo conteso esce in coppia con quello della
sorgente più un booleano che dice quale si sta guardando — il booleano e non la
sola differenza fra i due valori, perché chi riscrive una descrizione può
riscriverla identica senza per questo voler smettere di averla sua.

`GET /api/users/{id}/categories` e `PATCH .../categories/{id}` sono le stesse due
operazioni dei conti, e le mancanti dicono la stessa cosa.

L'importazione gira **dentro** il backend e non in un processo separato. La
ragione decisiva è il segreto: finché l'import stava altrove serviva un endpoint
che gli consegnasse il token in chiaro, ed era l'unico punto in cui una
credenziale lasciava il processo che possiede la chiave per decifrarla. Ora non
serve più, e infatti `/internal` non esiste.

L'endpoint è sincrono. Misurato sui dati reali: ~1,5 s per un import
incrementale, ~3 s per uno completo (1677 movimenti, dieci chiamate alla
sorgente, con il rate limit della sorgente a 300 richieste/ora per token). A
questi tempi un 202 con polling sarebbe macchinario prematuro; il corpo della
risposta — un esito per sorgente — è già la forma giusta da restituire in
differita il giorno che servisse.

Un fallimento non è un errore HTTP: le sorgenti di un utente sono indipendenti, e
se una su tre non risponde le altre due hanno comunque lavorato. La risposta è
sempre 200 con un esito per sorgente e un `failure` su quella caduta — un 500
nasconderebbe il lavoro riuscito.

La radice del modulo porta `@ApplicationModule` nel `package-info.java`:
Spring Modulith è configurato con la detection strategy
`explicitly-annotated`, quindi un package non annotato non è un modulo e i
suoi confini non sono verificati. La mappa stampata da `ModularityTests` lo
rende evidente.

Violare i confini fra moduli non sarà un'opinione, sarà un test che fallisce:

```
$ ./gradlew test --tests 'it.walletinsight.ModularityTests'
Module 'budgets' depends on module 'analytics' via ... Allowed targets: records.
```

`./gradlew test` genera anche i diagrammi in `build/spring-modulith-docs`.

## Convenzioni

Queste convenzioni restano valide e si applicheranno ai moduli futuri:

- **Identificatori UUIDv7.** Li genera l'applicazione (`Uuids.v7()` in
  `shared/identifier`), mai il database con ID tecnici: restano stabili
  migrando i dati fra ambienti e, essendo sequenziali nel tempo, gli indici
  B-tree ricevono inserimenti sempre in coda.
- **Importi interi in centesimi.** `Money` non diventa mai `BigDecimal`,
  nemmeno nel JSON: il frontend somma in interi per evitare il floating
  point.
- **Date senza fuso.** `LocalDate`, serializzata `yyyy-MM-dd`.
- **Enum in kebab-case** nel JSON (`credit-card`), tradotti da `KebabCase`.
- **`Page<T>` è nostro**: `{items, total, index, size, pageCount}`, mai
  quello di Spring Data.
- **Errori come `ProblemDetail`** (RFC 9457).
- **Le aggregazioni le fa PostgreSQL**, ma l'SQL vive in
  `infrastructure/jdbc`: cambiare database tocca solo quel layer.
