# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Contesto

`frontend/` è uno dei due progetti del monorepo **Wallet Insights** (`/Volumes/Programmazione/Progetti/margine`):
`frontend/` (Angular 22 + Nx, questo) e `backend/` (Spring Boot, Gradle), che include anche
l'importazione dalle sorgenti esterne. Ognuno ha il proprio build system e si sviluppa in
isolamento; `compose.yaml` alla radice li avvia insieme (Postgres :5432, backend :8080,
frontend :8081) leggendo `.env` (template in `.env.example`).

L'app è un **PoC**, ma tutto parla col backend: profilo, primo accesso, sorgenti
collegate, conti, **movimenti, categorie, budget**, classifiche di periodo e KPI. Non ci
sono mock.

I **budget** sono un'area loro (`libs/modules/budgets/{domain,data-access,feature}`, rotta
`/budget`): limiti mensili su gruppi di categorie, con sotto-budget su parte delle categorie
del principale. La pagina mostra un mese alla volta (‹ mese ›, non oltre quello corrente)
col resoconto calcolato dal backend. Nel pannello si può spuntare una macro per prenderne
tutte le sottocategorie libere (è una scorciatoia: il budget salva le sottocategorie) — speso, residuo, «fuori budget» — e un pannello per
crearli e modificarli. Le regole fra budget le decide il server (409 col motivo, mostrato
com'è); il pannello spegne in anticipo le categorie già prese e dice quale budget le tiene.
Anche la **Panoramica** riassume il mese corrente — sempre quello, qualunque periodo sia
scelto, perché i limiti sono mensili — coi quattro principali più consumati; per questo
`BudgetMeter` sta in `budgets-ui` e `scope:overview` vede `scope:budgets`.

Gli **abbonamenti** sono un'area loro (`libs/modules/subscriptions/{domain,data-access,feature}`,
rotta `/abbonamenti`): l'utente li scrive (nome, importo, cadenza «ogni N
settimane/mesi/anni», primo addebito, fine facoltativa = disdetto, categoria e conto
facoltativi). Due viste: **Elenco** — addebiti in arrivo (7/30/90 giorni) col totale, attivi
raggruppati per categoria, conclusi — e **Calendario**, il mese in griglia (su telefono i
soli giorni con un addebito). Accanto, il costo mensile e annuo. Le righe hanno la grammatica
di `app-record-row` (niente divisori, barra del conto). Prossimo addebito, «oggi» e totali
arrivano dal server: l'area non legge il profilo, ma vede `scope:accounts` per i conti. La
modifica è un `PUT` della definizione intera. Il pannello di modifica sta in
`subscriptions-ui` perché lo usano due aree: dal dettaglio di un'**uscita** nei Movimenti,
«Aggiungi agli abbonamenti» lo apre compilato con quel movimento (`subscriptionDraftFrom`:
nome, importo, categoria e conto suoi, lui come primo addebito, cadenza mensile da
confermare). Nessun rilevamento automatico delle ricorrenze: è stato misurato sui dati reali
e scartato, perché le regole non reggevano — l'abbonamento lo dichiara l'utente. `YearMonth`/`shiftMonth` stanno ora in
`shared-domain`, `formatMonth`, `parseAmount` e `amountInput` in `shared-ui`: li usano Budget e
Abbonamenti.

Le **categorie** sono di Wallet Insights, non della sorgente: `Category` ha `parentId` (macro →
sottocategoria, `categoryTree()` le raggruppa) e i movimenti stanno solo nelle
sottocategorie. Si gestiscono in Impostazioni → Categorie (rinomina, sposta, crea, unisci) e
lì si decide anche in quale categoria confluisce ognuna di BudgetBakers (`SourceCategory`).

`DemoAccount` non esiste più: c'era perché i movimenti finti erano agganciati a conti finti,
e `shared` non può nominare un'area. Ora `AccountsOverview` vive in
`@wallet/accounts-data-access` e lavora sul conto vero — l'area `accounts` espone il proprio
facade come già facevano `user` e `ingestion`, e `core`, `overview` e `movements` la vedono.

Anche il modello dei movimenti è cambiato di forma, ed è la forma del backend:
`FinanceRecord.amount` è **con segno**, descrizione e controparte sono coppie
`SourcedText` (valore dell'utente accanto a quello della sorgente, più il flag `edited`), e
non esistono più `payee`, `note`, `paymentMethod`, `labels`, `recurring` e
`counterAccountId` — la sorgente reale non li dichiara.

L'architettura è invece definitiva — vedi `README.md`, che è la documentazione di
riferimento (mappa del monorepo, come aggiungere una sezione, come collegare il backend,
design system, convenzioni). Questo file riassume solo ciò che serve per operare.

## Comandi

Sempre da `frontend/`. Node ≥ 22.22.3 o ≥ 24.15 (`.nvmrc`: 24.21.0).

```bash
npm start                    # dev server su :4200, /api inoltrato a localhost:8080
npm run build                # build produzione in dist/apps/wallet
npm run build:single-file    # dist/wallet-insights-standalone.html, apribile offline
npm run lint                 # ESLint su tutti i progetti + confini fra moduli
npm test                     # test di tutti i progetti
npm run verify               # lint + test + build, la sequenza da eseguire prima di consegnare
npm run graph                # grafo delle dipendenze fra librerie
npx nx affected -t lint test build   # solo ciò che è cambiato
```

Target su un singolo progetto (nomi Nx, **diversi dai percorsi**: `shared-domain`,
`shared-ui`, `shared-data-access`, `core-shell`,
`overview-feature`, `movements-feature`, `budgets-feature`, `subscriptions-feature`, `settings-feature`, `upcoming-feature`,
`accounts-domain`, `accounts-data-access`, `budgets-domain`, `budgets-data-access`, `budgets-ui`,
`subscriptions-domain`, `subscriptions-data-access`, `subscriptions-ui`,
`ingestion-domain`, `ingestion-data-access`,
`user-domain`, `user-data-access`, `onboarding-domain`, `onboarding-data-access`,
`onboarding-feature`, `wallet`):

```bash
npx nx test shared-domain
npx nx lint overview-feature
npx nx test shared-domain --testFiles=libs/shared/domain/src/lib/shared/money.spec.ts
npx vitest run --project shared-domain money      # filtro per file
npx vitest run --project shared-domain -t "somma" # filtro per nome del test
```

Nx mette in cache i target: aggiungere `--skip-nx-cache` quando serve una riesecuzione
reale. `npm run format` (Prettier) prima di consegnare modifiche estese.

I test delle librerie girano con Vitest (`@nx/vitest:test` + `@analogjs/vite-plugin-angular`,
un `vite.config.mts` per libreria, `passWithNoTests`). I test dell'app girano invece con
`@angular/build:unit-test`, che **compila l'intero grafo**: un errore di tipo in qualsiasi
libreria fa fallire `nx test wallet` con lo stesso messaggio di `nx build wallet`.

## Architettura

### Ports & adapters

`libs/shared/domain` è il centro e non dipende da nulla (nemmeno dalla UI): entità,
value object e **porte** — interfacce più un `InjectionToken` omonimo
(`RECORD_REPOSITORY`, `ANALYTICS_REPOSITORY`, …) in `src/lib/ports/*.port.ts`. Ogni
metodo di porta è `async` e accetta un `signal?: AbortSignal` finale.

Ogni modulo ha il proprio adapter HTTP nel suo `data-access` (`src/lib/http/`), cablato
da un `provide<Modulo>Http(): EnvironmentProviders` esportato dal barrel; le porte di
`shared-domain` le cabla `provideSharedHttp()` in `@wallet/shared-data-access`, che
contiene anche il client comune (`API_BASE_URL`, `requestJson`, `currentUserId`).
`apps/wallet/src/app/app.config.ts` li chiama tutti. Non iniettare mai una classe
repository concreta: non esce dal suo modulo, si inietta il token.

### Confini fra moduli (sono lint, non convenzioni)

Ogni progetto dichiara in `project.json` due tag, `scope:*` e `type:*`;
`eslint.config.mjs` li incrocia in `@nx/enforce-module-boundaries`. Le due famiglie di
regole si sommano:

- **type**: `app → shell·feature·data-access·ui·domain`, `shell|feature → data-access·ui·domain`,
  `data-access → data-access·domain`, `ui → ui·domain` (una UI d'area compone `shared-ui`), `domain → domain` (un caso d'uso che attraversa due aree, come
  l'onboarding, deve poterle nominare entrambe; a impedire che un'area ne conosca un'altra
  resta il tag `scope`).
- **scope**: ogni area vede solo se stessa e `scope:shared`; `scope:app` vede tutto.

Conseguenze: una feature non può importare un'altra feature (ciò che serve a due sezioni
sale in `shared`), e solo l'app conosce l'adapter dei dati. Ogni libreria è raggiungibile
**solo** dal suo `src/index.ts` — gli import profondi sono errori di lint. Le feature
esportano dal barrel **soltanto le rotte**.

Aggiungendo una libreria vanno aggiornati: `project.json` (tags), `tsconfig.base.json`
(path `@wallet/*`) e, per una nuova area, `eslint.config.mjs` (voce `scopeConstraints`).
La procedura completa, con il comando del generatore, è nel README.

Le **Impostazioni** non stanno nel menu principale: ci si arriva dal blocco con nome e
piano in fondo alla sidebar (`ACCOUNT_NAVIGATION` in `core/shell`). Quel menu elenca i posti
dove si guardano i propri soldi, le impostazioni sono un'altra cosa. La sezione ha un
guscio con menu laterale (`SETTINGS_SECTIONS`) e rotte figlie con percorsi **relativi**:
`profilo`, `conti`, e `piano` annunciato ma senza rotta. Il facade è fornito dal guscio, non
dalle pagine, così cambiare scheda non ricarica conti e sorgenti.

**Non c'è più un «aggiorna ora»**, né in Impostazioni né altrove, e la porta che lo
serviva non esiste più nel dominio. L'importazione ha due momenti e nessuno dei due è un
pulsante: il primo import parte dal **primo accesso** — `POST /api/onboarding` collega la
sorgente e ne legge subito tutto lo storico, quindi quella chiamata dura qualche secondo e
il pulsante di invio lo dice («Importo i tuoi movimenti…») — e gli aggiornamenti li fanno
le **schedulazioni** del backend. Su una sorgente questa schermata mostra le due
date, `lastRecordDate` e `lastRunAt`: fino a quando si hanno i dati, e quando si è
guardato l'ultima volta. L'unico gesto è **sostituire il token**: quando la sorgente lo
rifiuta (`credentialsRejectedAt`) la scheda lo dice e apre il campo, e la topbar mostra
«Token da aggiornare» con il link qui. Le sorgenti stanno in `ImportConnections`
(`@wallet/ingestion-data-access`), una copia sola condivisa da topbar e Impostazioni.

### Anatomia di una feature

`libs/modules/<area>/feature/src/lib/` → `data-access/` (il facade), `ui/` (widget di
presentazione), `pages/` (la pagina che orchestra), `<nome>.routes.ts`.

- La pagina fornisce il proprio facade con `providers: [XFacade]`: il ciclo di vita è
  della pagina, non globale. Il facade è `@Injectable()` senza `providedIn`.
- Il facade tiene lo stato in `signal`, deriva con `computed` e carica ogni fonte dati con
  `resource({ params, loader: ({ params, abortSignal }) => porta.metodo(params, abortSignal) })`.
  Tiene un array privato `sources` da cui derivano `isLoading` / `hasError` e su cui
  itera `refresh()`.
- I widget ricevono dati via `input()` ed emettono intenzioni via `output()`; non
  conoscono i repository.
- Le rotte usano `loadComponent`/`loadChildren`: ogni sezione è un chunk.

### Vincoli tecnici da rispettare

- **Zoneless** (default di Angular 22): nessuna Zone.js, la reattività passa solo dai signal.
- `ChangeDetectionStrategy.OnPush` **obbligatorio** su ogni componente (regola ESLint), così
  come componenti standalone e `@if`/`@for` nei template (`prefer-control-flow`).
- Stile Angular ≥ 20: `input()`/`output()`/`model()` al posto dei decoratori, nessun
  suffisso `.component`, selettori `app-*` (kebab-case per i componenti, camelCase con
  prefisso `app` per le direttive).
- I form si scrivono con i **signal form** (`@angular/forms/signals`), mai con i reactive
  form: modello in un `signal`, regole in uno `schema()` a livello di modulo, `<form
[formRoot]>` e `[formField]` sui controlli. È `[formRoot]` a intercettare l'invio e a
  impedire il submit nativo del browser.
- **Importi sempre `Money`**, cioè centesimi interi, e le operazioni passano dalle funzioni
  di `money.ts` (`addMoney`, `sumMoney`, …) che verificano la valuta. Mai `number` grezzi.
- **Id branded** (`AccountId`, `RecordId`, …): si costruiscono con `asAccountId(raw)`.
- **Filtri, ordinamento, totali e paginazione dei movimenti li fa il database**, mai il
  client: il facade compone una `RecordQuery` e l'adapter la traduce in querystring. Su 1678
  movimenti scaricare tutto per filtrarlo nel browser sarebbe già una cattiva idea, e lo
  storico cresce a ogni import. Non c'è ricerca testuale: è rimandata, perché farla bene
  vuol dire un indice apposta e decidere cosa significhi «trovare».
- La schermata Movimenti però **chiede solo il periodo**: dieci scelte — nove preset più
  le date libere — e nient'altro. «Sempre» è stato tolto: per guardare lo storico intero
  restano le date libere. Il conto arriva dall'URL (`/movimenti?conto=<id>`): ci si
  arriva cliccando un conto nella sidebar, la pagina lo mostra come chip con la × per
  tornare a tutti, e restringe allo stesso conto anche le due classifiche laterali
  (`SpendingScope`, `?accountId=` sugli endpoint di analytics). Ordinamento (fisso sui
  più recenti) e filtri per tipo e categoria restano interi nel dominio, nella query e nel
  backend, ma non hanno un comando: il pannello che li offriva elencava 95 categorie in 402 pixel di chip, cioè
  offriva tutto senza rendere sceglibile niente. Rimetterne uno è una riga nel facade.
- Di un movimento si correggono **tre cose e tre soltanto**: descrizione, controparte
  (etichettata «Pagato a» o «Ricevuto da» secondo il verso) e categoria. Importo, data,
  verso e stato no — arrivano dall'import e ogni aggiornamento li riscrive.
- **Il frontend non sa niente della sorgente**, ed è una richiesta esplicita: nel dominio
  `description` e `counterParty` sono una stringa sola, non una coppia
  «valore dell'utente accanto a valore importato», e non esistono `categoryReassigned` né
  `sourceCategoryName`. Non lo sa nemmeno il database: i campi sono uno ciascuno
  anche là, scritti dall'import quando il movimento entra e poi mai più. Il pannello mostra i campi, salvando manda quei
  campi, e svuotarne uno lo lascia vuoto. Il pannello mostra sempre la risposta del server,
  mai la bozza appena digitata.
- **Un movimento `uncleared` non si corregge** (`awaitingSettlement` nel dominio): la banca
  non l'ha ancora confermato, e quando lo farà la sorgente lo sostituirà con un altro
  movimento dall'identificativo diverso — quello che ci si scrivesse sopra sparirebbe con
  lui. La riga si smorza e porta un «in attesa» accanto al titolo (il colore non è mai
  l'unico canale), e il pannello mostra i tre valori in sola lettura sotto il badge «In
  attesa di contabilizzazione», invece di tre caselle spente. Il backend risponde comunque
  409: la regola non è dell'interfaccia.

- TypeScript stretto: `strict`, `noUncheckedIndexedAccess`, `noUnusedLocals`,
  `noPropertyAccessFromIndexSignature`, `strictTemplates`. ESLint impone `interface`
  (non `type`) per le definizioni di oggetti.
- Il routing è **con hash** (`withHashLocation`): l'app resta apribile da hosting statico
  e da `file://`, ed è ciò che rende possibile la build a file singolo.
- ECharts vive nell'entry point secondario `@wallet/shared-ui/echarts`, caricato on demand
  da `provideEchartsCore`: non importarlo dal barrel principale, finirebbe nel bundle iniziale.
- Budget di bundle in `apps/wallet/project.json`: 600 kB di warning, 1.5 MB di errore
  sull'initial.

### UI e testi

I token di design stanno in `libs/shared/ui/src/styles/design-system.css` (Tailwind 4 via
`@tailwindcss/postcss`); i componenti li danno per scontati. `ThemeStore` aggiorna
`data-theme` **prima** dei signal, perché i grafici leggono i token con `getComputedStyle`.
Le regole visive (un solo accento per schermata, niente palette categoriale, il colore mai
come unico canale) sono nel README e sono vincolanti. Lo sono anche quelle per il
**telefono** (sotto `sm`): poche informazioni ma buone — niente blocco di riepilogo né
righe di dettaglio secondarie (`hidden sm:block`), il comando principale a tutta riga
(`w-full sm:w-auto`), bersagli da almeno 40 px. Ogni schermata nuova va controllata anche lì. Pulsanti e campi
usano le classi `btn btn-*` e `field` del design system, gli errori `<app-alert>`: mai classi
riscritte a mano (tabella nel README).

Codice, commenti, documentazione e testi dell'interfaccia sono **in italiano**; i commenti
spiegano il _perché_. Le rotte sono in italiano (`/panoramica`, `/movimenti`), con redirect
dai vecchi percorsi inglesi in `app.routes.ts`.
