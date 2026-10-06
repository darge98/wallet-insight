import nx from '@nx/eslint-plugin';

/**
 * Confini fra moduli.
 *
 * Ogni libreria dichiara due tag in `project.json`:
 *
 *   scope:*  a quale area appartiene   (shared, core, overview, movements, …)
 *   type:*   che ruolo ha nello strato (domain, util, ui, data-access, feature, shell, app)
 *
 * Le due famiglie di regole si sommano: un import è lecito solo se passa
 * entrambe. È questo che tiene il grafo delle dipendenze aciclico e impedisce
 * che una feature finisca per dipendere da un'altra.
 */
const typeConstraints = [
  {
    // L'applicazione è il punto di composizione: monta tutto, non è montata da nessuno.
    sourceTag: 'type:app',
    onlyDependOnLibsWithTags: [
      'type:shell',
      'type:feature',
      'type:data-access',
      'type:ui',
      'type:domain',
      'type:util',
    ],
  },
  {
    // La shell è il guscio: conosce la UI condivisa, non le feature.
    sourceTag: 'type:shell',
    onlyDependOnLibsWithTags: ['type:data-access', 'type:ui', 'type:domain', 'type:util'],
  },
  {
    // Una feature non dipende mai da un'altra feature.
    sourceTag: 'type:feature',
    onlyDependOnLibsWithTags: ['type:data-access', 'type:ui', 'type:domain', 'type:util'],
  },
  {
    // Gli adapter parlano il linguaggio del dominio, e possono comporsi fra loro.
    //
    // La composizione serve a uno stato condiviso e non a un adapter che ne chiama
    // un altro per caricare dati: `AccountsOverview` segnala a `SyncStatus` che i
    // conti sono arrivati, e la topbar lo mostra. Resta vietato tutto ciò che
    // guarda in su — feature, UI, shell — che è l'intento della regola.
    sourceTag: 'type:data-access',
    onlyDependOnLibsWithTags: ['type:data-access', 'type:domain', 'type:util'],
  },
  {
    // Una UI d'area compone quella condivisa (`subscriptions-ui` usa icone e campi
    // di `shared-ui`). Il verso resta uno solo: `shared-ui` non può nominare
    // un'area, lo vieta il suo `scope`, quindi non nasce nessun ciclo.
    sourceTag: 'type:ui',
    onlyDependOnLibsWithTags: ['type:ui', 'type:domain', 'type:util'],
  },
  {
    // Il dominio è il livello più basso: non conosce la UI né gli adapter. Può
    // comporre altri domini — un caso d'uso che attraversa due aree deve pur
    // nominarle entrambe — ma solo dove i tag `scope` glielo consentono, che è
    // ciò che continua a impedire a un'area di conoscerne un'altra.
    sourceTag: 'type:domain',
    onlyDependOnLibsWithTags: ['type:domain', 'type:util'],
  },
  { sourceTag: 'type:util', onlyDependOnLibsWithTags: ['type:util'] },
];

const scopeConstraints = [
  { sourceTag: 'scope:app', onlyDependOnLibsWithTags: ['*'] },
  // L'area `user` espone il profilo tramite il proprio facade: è l'unica derivazione
  // ammessa dall'esterno, quindi le aree che lo consumano possono vederla.
  { sourceTag: 'scope:user', onlyDependOnLibsWithTags: ['scope:user', 'scope:shared'] },
  // Come `user`, l'area `ingestion` espone il proprio facade: è ciò che permette a
  // onboarding — e domani alle impostazioni — di collegare una sorgente dati.
  {
    sourceTag: 'scope:ingestion',
    onlyDependOnLibsWithTags: ['scope:ingestion', 'scope:shared'],
  },
  // Un conto porta con sé la sorgente da cui è stato importato: fa parte della sua
  // identità, non è un dettaglio dell'importazione. Il catalogo delle sorgenti vive
  // in `ingestion`, quindi l'area `accounts` deve poterlo nominare — come nel
  // backend, dove l'aggregato `Account` dichiara il proprio `IngestionSource`.
  {
    sourceTag: 'scope:accounts',
    onlyDependOnLibsWithTags: ['scope:accounts', 'scope:shared', 'scope:ingestion'],
  },
  // Le impostazioni sono la schermata che mette insieme le cose dell'utente: il suo
  // profilo, i suoi conti e le sorgenti da cui arrivano. È l'unica area che ne
  // nomina tre, e ne è la ragione d'essere.
  {
    sourceTag: 'scope:settings',
    onlyDependOnLibsWithTags: [
      'scope:settings',
      'scope:shared',
      'scope:user',
      'scope:accounts',
      'scope:ingestion',
    ],
  },
  {
    sourceTag: 'scope:onboarding',
    onlyDependOnLibsWithTags: ['scope:onboarding', 'scope:shared', 'scope:user', 'scope:ingestion'],
  },
  // Anche l'area `accounts` espone il proprio facade (`AccountsOverview`), e le tre
  // aree qui sotto lo consumano: la sidebar elenca i conti in ogni schermata, la
  // Panoramica ne conta quanti sono, i Movimenti ci filtrano sopra. Prima passavano
  // da un `DemoAccount` che viveva in `shared` proprio per aggirare questo confine;
  // ora che i movimenti sono veri quel modello non serve più, e la dipendenza è
  // dichiarata invece che nascosta. `ingestion` serve alla topbar, che avvisa
  // quando una sorgente ha rifiutato il token.
  {
    sourceTag: 'scope:core',
    onlyDependOnLibsWithTags: [
      'scope:core',
      'scope:shared',
      'scope:user',
      'scope:accounts',
      'scope:ingestion',
    ],
  },
  // La Panoramica vede i budget per riassumerne il mese corrente.
  {
    sourceTag: 'scope:overview',
    onlyDependOnLibsWithTags: [
      'scope:overview',
      'scope:shared',
      'scope:user',
      'scope:accounts',
      'scope:budgets',
    ],
  },
  // I Movimenti leggono il profilo per un solo motivo: la timezone con cui
  // risolvere "Oggi" e "Questa settimana". La stessa parola deve indicare lo
  // stesso giorno qui e nella Panoramica, che il profilo lo legge già; farlo
  // sul fuso del dispositivo vorrebbe dire due "oggi" diversi nella stessa app.
  //
  // Vedono anche gli abbonamenti: dal dettaglio di un'uscita la si registra come
  // abbonamento, col pannello di quell'area già compilato.
  {
    sourceTag: 'scope:movements',
    onlyDependOnLibsWithTags: [
      'scope:movements',
      'scope:shared',
      'scope:accounts',
      'scope:user',
      'scope:subscriptions',
    ],
  },
  // Come i Movimenti, i Budget leggono il profilo per il fuso: «questo mese» deve
  // essere lo stesso mese qui e nel backend, che fissa i limiti nuovi da lì.
  {
    sourceTag: 'scope:budgets',
    onlyDependOnLibsWithTags: ['scope:budgets', 'scope:shared', 'scope:user'],
  },
  // Gli abbonamenti non leggono il profilo: «oggi» arriva dal server, che lo calcola
  // nel fuso dell'utente insieme al prossimo addebito. Vedono i conti perché un
  // abbonamento dice da quale esce.
  {
    sourceTag: 'scope:subscriptions',
    onlyDependOnLibsWithTags: ['scope:subscriptions', 'scope:shared', 'scope:accounts'],
  },
  { sourceTag: 'scope:upcoming', onlyDependOnLibsWithTags: ['scope:upcoming', 'scope:shared'] },
  { sourceTag: 'scope:shared', onlyDependOnLibsWithTags: ['scope:shared'] },
];

export default [
  ...nx.configs['flat/base'],
  ...nx.configs['flat/typescript'],
  ...nx.configs['flat/javascript'],
  {
    ignores: ['**/dist', '**/out-tsc', '**/coverage', '**/vitest.config.*.timestamp*'],
  },
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.js', '**/*.jsx'],
    rules: {
      '@nx/enforce-module-boundaries': [
        'error',
        {
          enforceBuildableLibDependency: true,
          allow: ['^.*/eslint(\\.base)?\\.config\\.[cm]?[jt]s$'],
          depConstraints: [...typeConstraints, ...scopeConstraints],
        },
      ],
    },
  },
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.cts', '**/*.mts', '**/*.js', '**/*.jsx', '**/*.mjs'],
    rules: {
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/consistent-type-definitions': ['error', 'interface'],
    },
  },
];
