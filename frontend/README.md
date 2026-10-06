# Wallet Insights

Personal finance tracker in **Angular 22** dentro un monorepo **Nx**. Legge i
dati del wallet (conti, movimenti, budget) e li presenta in una Panoramica
aggiornabile e in una sezione Movimenti con ricerca e filtri.

> **Stato: Proof of Concept.** L'architettura è definitiva, i dati no: al posto
> delle API BudgetBakers gira un adapter in memoria. Sostituirlo è **una riga**
> in `apps/wallet/src/app/app.config.ts` — vedi [Collegare il backend](#collegare-il-backend).

---

## Indice

- [Requisiti](#requisiti)
- [Avvio rapido](#avvio-rapido)
- [Script](#script)
- [Mappa del monorepo](#mappa-del-monorepo)
- [Confini fra moduli](#confini-fra-moduli)
- [Aggiungere una sezione](#aggiungere-una-sezione)
- [Collegare il backend](#collegare-il-backend)
- [Come è fatta una feature](#come-è-fatta-una-feature)
- [Design system](#design-system)
- [Convenzioni di codice](#convenzioni-di-codice)
- [Roadmap](#roadmap)

---

## Requisiti

| Strumento | Versione            |
| --------- | ------------------- |
| Node.js   | ≥ 22.22.3 o ≥ 24.15 |
| npm       | ≥ 10                |

## Avvio rapido

```bash
npm install
npm start          # http://localhost:4200
```

## Script

| Comando                     | Cosa fa                                                  |
| --------------------------- | -------------------------------------------------------- |
| `npm start`                 | Dev server dell'applicazione                             |
| `npm run build`             | Build di produzione (`dist/apps/wallet`)                 |
| `npm run build:single-file` | `dist/wallet-insights-standalone.html`, apribile offline |
| `npm test`                  | Test di tutti i progetti                                 |
| `npm run lint`              | ESLint **e verifica dei confini fra moduli**             |
| `npm run verify`            | lint + test + build, come in CI                          |
| `npm run graph`             | Apre il grafo delle dipendenze fra librerie              |
| `npm run format`            | Prettier sui file toccati                                |

Nx mette in cache i risultati: rilanciare un target che non ha input modificati
è istantaneo. Per lavorare solo su ciò che è cambiato:

```bash
npx nx affected -t lint test build
```

---

## Mappa del monorepo

```
apps/
└── wallet/                 applicazione: composizione e basta
libs/
├── core/
│   └── shell/              sidebar, topbar, area instradata
├── shared/
│   ├── util/               date, periodi, fusi orari
│   ├── domain/             entità, value object, porte
│   ├── ui/                 design system: token, componenti, pipe, tema
│   └── data-access/        stato condiviso, client HTTP comune, adapter delle porte di shared
└── modules/                un'area di dominio per cartella, con i suoi strati
    ├── user/               profilo dell'utente: domain · data-access
    ├── accounts/           i conti: domain · data-access (`AccountsOverview`)
    ├── ingestion/          sorgenti collegate e credenziali: domain · data-access
    ├── onboarding/         primo accesso: domain · data-access · feature
    ├── overview/feature/   Panoramica
    ├── movements/feature/  Movimenti
    ├── budgets/            limiti mensili per gruppi di categorie: domain · data-access · feature
    ├── settings/feature/   Impostazioni: Profilo · Conti · Piano (annunciato)
    └── upcoming/feature/   Assistente AI · Abbonamenti
```

C'è **un** modello di conto solo. Ce n'erano due — `Account` e un `DemoAccount` nel
dominio condiviso — perché i movimenti finti erano agganciati a conti finti, e
`scope:shared` non può nominare un'area. Il giorno in cui i movimenti sono passati al
backend quel modello non è più servito: `AccountsOverview` è sceso in
`libs/modules/accounts/data-access` e lavora sui conti veri, e l'area `accounts` espone il
proprio facade come già facevano `user` e `ingestion`. La dipendenza di `core`,
`overview` e `movements` su `accounts` è ora dichiarata in `eslint.config.mjs`
invece di essere aggirata da un tipo parcheggiato in `shared`.

| Libreria                              | Import                           | `scope:`     | `type:`       |
| ------------------------------------- | -------------------------------- | ------------ | ------------- |
| `libs/shared/util`                    | `@wallet/shared-util`            | `shared`     | `domain`      |
| `libs/shared/domain`                  | `@wallet/shared-domain`          | `shared`     | `domain`      |
| `libs/shared/ui`                      | `@wallet/shared-ui`              | `shared`     | `ui`          |
| `libs/shared/data-access`             | `@wallet/shared-data-access`     | `shared`     | `data-access` |
| `libs/modules/user/domain`            | `@wallet/user-domain`            | `user`       | `domain`      |
| `libs/modules/user/data-access`       | `@wallet/user-data-access`       | `user`       | `data-access` |
| `libs/modules/accounts/domain`        | `@wallet/accounts-domain`        | `accounts`   | `domain`      |
| `libs/modules/accounts/data-access`   | `@wallet/accounts-data-access`   | `accounts`   | `data-access` |
| `libs/modules/ingestion/domain`       | `@wallet/ingestion-domain`       | `ingestion`  | `domain`      |
| `libs/modules/ingestion/data-access`  | `@wallet/ingestion-data-access`  | `ingestion`  | `data-access` |
| `libs/modules/onboarding/domain`      | `@wallet/onboarding-domain`      | `onboarding` | `domain`      |
| `libs/modules/onboarding/data-access` | `@wallet/onboarding-data-access` | `onboarding` | `data-access` |
| `libs/core/shell`                     | `@wallet/core-shell`             | `core`       | `shell`       |
| `libs/modules/overview/feature`       | `@wallet/overview-feature`       | `overview`   | `feature`     |
| `libs/modules/movements/feature`      | `@wallet/movements-feature`      | `movements`  | `feature`     |
| `libs/modules/budgets/domain`         | `@wallet/budgets-domain`         | `budgets`    | `domain`      |
| `libs/modules/budgets/data-access`    | `@wallet/budgets-data-access`    | `budgets`    | `data-access` |
| `libs/modules/budgets/ui`             | `@wallet/budgets-ui`             | `budgets`    | `ui`          |
| `libs/modules/budgets/feature`        | `@wallet/budgets-feature`        | `budgets`    | `feature`     |
| `libs/modules/settings/feature`       | `@wallet/settings-feature`       | `settings`   | `feature`     |
| `libs/modules/onboarding/feature`     | `@wallet/onboarding-feature`     | `onboarding` | `feature`     |
| `libs/modules/upcoming/feature`       | `@wallet/upcoming-feature`       | `upcoming`   | `feature`     |

**Ogni libreria è una scatola con una sola apertura**: il suo `src/index.ts`.
Quello che non è esportato lì non è raggiungibile da fuori — il lint blocca gli
import "profondi". È questo che rende sicuro cambiare l'interno di una libreria
senza rompere nessuno.

Le feature, per esempio, esportano **solo le rotte**: pagine, widget e facade
restano affari loro.

---

## Confini fra moduli

Le regole vivono in `eslint.config.mjs` e sono due famiglie che si sommano —
un import passa solo se le soddisfa entrambe.

**Per tipo** — chi può dipendere da chi, verso il basso:

```
app     →  shell · feature · data-access · ui · domain
shell   →  data-access · ui · domain
feature →  data-access · ui · domain
data-access →  domain
ui          →  domain
domain      →  (niente)
```

**Per area** — ogni area vede se stessa e `shared`, mai le altre:

```
scope:app        →  tutto
scope:overview   →  scope:overview · scope:shared
scope:movements  →  scope:movements · scope:shared
…
scope:shared     →  scope:shared
```

Le conseguenze pratiche:

- una feature **non** può importare un'altra feature: se due sezioni hanno
  bisogno della stessa cosa, quella cosa sale in `shared`;
- il dominio **non** può importare la UI: resta puro e testabile senza Angular;
- solo l'applicazione conosce l'adapter dei dati.

Provare a violarli non è un'opinione, è un errore di lint:

```
$ npx nx lint overview-feature
error  A project tagged with "type:feature" can only depend on libs tagged with
       "type:data-access", "type:ui", "type:domain", "type:util"
```

`npm run graph` disegna il grafo risultante.

---

## Aggiungere una sezione

Il caso più frequente, e il motivo per cui il repo è fatto così: si **aggiunge**,
non si modifica.

```bash
npx nx g @nx/angular:library libs/modules/goals/feature \
  --name=goals-feature --importPath=@wallet/goals-feature \
  --unitTestRunner=vitest-analog --style=none --prefix=app \
  --tags=scope:goals,type:feature
```

Poi tre righe, una per file:

1. `libs/modules/goals/feature/src/index.ts` → `export * from './lib/goals.routes';`
2. `apps/wallet/src/app/app.routes.ts` → una voce `loadChildren`
3. `libs/core/shell/src/lib/navigation.ts` → una voce di menu
4. `eslint.config.mjs` → `{ sourceTag: 'scope:goals', onlyDependOnLibsWithTags: ['scope:goals', 'scope:shared'] }`

Nessun file esistente cambia comportamento. La sezione nasce già isolata: non
può importare le altre feature, e le altre non possono importare lei.

Se la sezione è solo annunciata, non serve nemmeno una libreria: basta una voce
in `libs/modules/upcoming/feature/src/lib/upcoming-section.ts` e una rotta. Quella
pagina è parametrica apposta — il contenuto è configurazione, non codice.

---

## Collegare il backend

Ogni modulo collega da sé le proprie porte: l'adapter HTTP sta nel suo
`data-access`, accanto al facade, in `src/lib/http/`.

- `http-<cosa>.repository.ts` implementa la porta del `domain` del modulo;
- `<cosa>-contract.ts` traduce le forme JSON nel dominio, e resta interno;
- `<modulo>-http.providers.ts` esporta `provide<Modulo>Http()`, l'unica cosa che
  il barrel espone dell'adapter.

Il client comune — `API_BASE_URL`, `requestJson`, `currentUserId`,
`PageResponse`, `contractError` — sta in `@wallet/shared-data-access`, insieme
agli adapter delle porte di `shared-domain` (movimenti, categorie, classifiche,
KPI) cablati da `provideSharedHttp()`.

`API_BASE_URL` vale `/api`, relativo, ovunque: nello stack lo inoltra nginx, con
`npm start` il proxy del dev server (`apps/wallet/proxy.conf.json`) verso
`localhost:8080` — il backend del compose o un `bootRun`, indifferentemente.

`apps/wallet/src/app/app.config.ts` chiama un `provide…Http()` per modulo. Le
classi repository non escono mai dal loro modulo: si inietta il token.

> **Autenticazione.** Le credenziali BudgetBakers non devono mai finire nel
> client: l'app dovrà parlare con un backend proprio, che custodisce il token e
> fa da proxy verso le API.

---

## Come è fatta una feature

Tre cartelle, sempre le stesse:

```
libs/modules/overview/feature/src/lib/
├── data-access/     OverviewFacade — lo stato della sezione
├── ui/              widget di presentazione
├── pages/           la pagina che li orchestra
└── overview.routes.ts
```

1. La **pagina** fornisce il proprio **facade** (`providers: [OverviewFacade]`):
   ciclo di vita legato alla pagina, non globale.
2. Il facade tiene lo stato in `signal` e deriva la query con `computed`.
3. Ogni fonte dati è una `resource()`: gestisce caricamento, errore e
   annullamento della richiesta precedente (`AbortSignal`).
4. I **widget** ricevono dati e restituiscono intenzioni. Non sanno da dove
   arrivano i numeri.

```ts
readonly summary = resource({
  params: () => this.range(),                        // cambia il periodo → ricarica
  loader: ({ params, abortSignal }) =>
    this.analytics.kpiSummary(params, abortSignal),  // porta, non implementazione
});

refresh(): void {
  for (const source of this.sources) source.reload();
}
```

### Scelte tecniche degne di nota

| Scelta                                    | Motivo                                                                                   |
| ----------------------------------------- | ---------------------------------------------------------------------------------------- |
| **Zoneless** (default di Angular 22)      | Nessuna Zone.js: la reattività passa solo dai signal.                                    |
| **`OnPush` ovunque** (regola ESLint)      | Change detection prevedibile e verificabile in lint.                                     |
| **Importi in centesimi interi** (`Money`) | Elimina gli errori di arrotondamento nelle somme di transazioni.                         |
| **Id tipizzati** (branded types)          | `AccountId` non è assegnabile a `CategoryId`: errori a compile-time, zero costo runtime. |
| **Lazy loading per feature**              | Ogni sezione è un chunk; ECharts vive in un entry point secondario caricato on demand.   |
| **Routing con hash**                      | L'app gira da qualunque hosting statico senza rewrite lato server.                       |

---

## Design system

L'interfaccia segue il concept Figma **"Wallet Insights — Personal Finance · UI
concepts"**. I valori di colore sono le variabili pubblicate in quel file.

I token vivono in `libs/shared/ui/src/styles/design-system.css` — nella libreria
UI, perché sono il contratto visivo che i suoi componenti danno per scontato —
e l'applicazione li importa nel proprio foglio di stile.

| Token    | Chiaro    | Uso                                              |
| -------- | --------- | ------------------------------------------------ |
| `ink`    | `#192d28` | testo principale                                 |
| `muted`  | `#62716a` | testo secondario                                 |
| `accent` | `#22634c` | un solo accento: margine, stati attivi, grafico  |
| `soft`   | `#e3eee7` | riempimenti tenui (pill di nav, pannello budget) |
| `line`   | `#dce3df` | bordi 1px                                        |

`ThemeStore` aggiorna `data-theme` **prima** dei signal, così i grafici — che
leggono i token via `getComputedStyle` — restano coerenti con il tema attivo.

### Regole visive

- **Un solo accento per schermata.** Il verde marca il margine, la voce attiva e
  la curva. Se tutto è evidenziato, niente lo è.
- **Non tutto è una card.** I KPI sono testo nudo, le liste non hanno divisori,
  e c'è un solo blocco pieno per pagina.
- **Il colore non è mai l'unico canale.** Gli stati del budget hanno icona ed
  etichetta; gli importi hanno segno oltre che tinta.
- **Niente palette categoriale.** Le classifiche codificano la grandezza con la
  lunghezza della barra e usano una sola tinta.

### Su telefono: poche informazioni, ma buone

«Telefono» vuol dire sotto `sm` (640 px). Lì ogni schermata risponde alla sua domanda
e basta:

- **Niente riepiloghi che ripetono la lista.** Il blocco pieno di sintesi (il
  riepilogo del mese nei Budget) si nasconde con `hidden sm:block`: su telefono la
  lista è già la risposta.
- **Niente righe di dettaglio secondarie.** Ciò che spiega un numero senza
  cambiarlo — l'elenco delle categorie sotto un budget — resta da `sm` in su.
- **Il comando principale prende tutta la riga** (`w-full sm:w-auto`): il
  selettore del mese nei Budget, il periodo nei Movimenti. I bersagli toccabili
  sono di almeno 40 px (`size-10 sm:size-8`).

Togliere su telefono non vuol dire perdere: ciò che si nasconde deve essere un di
più, mai l'unico posto in cui un'informazione si legge.

### Comandi, campi, errori

Non si riscrivono a mano: le classi stanno in `design-system.css` (`@layer components`)
e sono già alte 40 px su telefono, 36 da `sm` in su.

| Classe              | Per                                                                      |
| ------------------- | ------------------------------------------------------------------------ |
| `btn btn-primary`   | il comando della schermata, uno per pagina: è un accento                 |
| `btn btn-secondary` | gli altri comandi con un peso («Nuovo budget», «Nuova macro»)            |
| `btn btn-text`      | i comandi che si leggono come testo («Modifica», «Annulla»)              |
| `btn btn-danger`    | la conferma di ciò che non si annulla («Elimina», «Unisci ed elimina»)   |
| `btn btn-icon`      | i pulsanti con la sola icona (chiudi, frecce, menu)                      |
| `field`             | input, select e textarea                                                 |
| `hit-area`          | allarga a 40 px l'area toccabile di un comando piccolo senza ingrandirlo |

Gli errori di pagina usano `<app-alert>` (`@wallet/shared-ui`): riquadro, icona,
testo, e le azioni come contenuto. Le modifiche di un elemento di una lista si fanno
in un pannello laterale (dettaglio movimento, budget, categoria), non con comandi
sparsi sulla riga.

Tipografia: **DM Sans**, con una scala dichiarata come utility
(`type-display`, `type-figure`, `type-section`, `type-eyebrow`). Gli importi
usano `tnum`, così le colonne restano allineate.

---

## Convenzioni di codice

- **Stile Angular ≥ 20**: componenti standalone, niente suffisso `.component`,
  `input()` / `output()` / `model()` al posto dei decoratori.
- **Naming**: il file dichiara il ruolo dove aggiunge significato
  (`*.port.ts`, `*.facade.ts`, `*.repository.ts`, `*.pipe.ts`, `*.routes.ts`).
- **TypeScript stretto**: `strict`, `noUncheckedIndexedAccess`, `noUnusedLocals`,
  `strictTemplates`.
- **Una responsabilità per unità**: le pagine orchestrano, i widget mostrano, i
  facade coordinano, i repository recuperano.
- **Commenti sul _perché_**, non sul _cosa_.
- **Accessibilità**: ruoli ARIA sui controlli custom, focus visibile, contrasti
  verificati in entrambi i temi, rispetto di `prefers-reduced-motion`.

---

## Roadmap

- [x] Monorepo Nx con confini fra moduli verificati dal lint
- [x] Panoramica, Movimenti, sezioni in arrivo
- [x] Tema chiaro/scuro con preferenza persistente
- [x] **Budget**: limiti mensili per gruppi di categorie, sotto-budget, storico dei limiti
- [x] Riepilogo dei budget in Panoramica
- [ ] Obiettivi di risparmio
- [ ] **Assistente AI**: domande in linguaggio naturale sui propri movimenti
- [ ] **Abbonamenti**: riconoscimento delle ricorrenze e calendario dei rinnovi
- [x] Adapter HTTP verso il backend, uno per modulo
- [ ] Creazione e modifica dei movimenti
