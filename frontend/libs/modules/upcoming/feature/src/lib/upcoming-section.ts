export interface UpcomingCapability {
  readonly title: string;
  readonly description: string;
}

export interface UpcomingStep {
  readonly title: string;
  readonly detail: string;
  readonly done: boolean;
}

export interface UpcomingSection {
  readonly title: string;
  readonly intro: string;
  readonly capabilities: readonly UpcomingCapability[];
  readonly roadmap: readonly UpcomingStep[];
}

export type UpcomingSectionKey = 'assistant';

const SHARED_BACKEND_STEP: UpcomingStep = {
  title: 'Integrazione con il backend',
  detail: 'Adapter HTTP verso le API, al posto della sorgente dati dimostrativa.',
  done: false,
};

/**
 * Contenuti delle sezioni annunciate.
 *
 * Tenere il testo qui, separato dalla pagina, permette di aprire una nuova
 * sezione "in arrivo" aggiungendo una voce di configurazione e una rotta —
 * nessun componente nuovo.
 */
export const UPCOMING_SECTIONS: Readonly<Record<UpcomingSectionKey, UpcomingSection>> = {
  assistant: {
    title: 'Assistente AI',
    intro:
      'Domande sui tuoi conti in linguaggio naturale: "quanto ho speso in ristoranti quest’anno", "cosa è cambiato rispetto a marzo". Risposte costruite sui tuoi movimenti, non su medie generiche.',
    capabilities: [
      {
        title: 'Domande sui movimenti',
        description:
          'Interrogazioni in italiano, con il dettaglio delle transazioni che compongono la risposta.',
      },
      {
        title: 'Riepiloghi automatici',
        description: 'Una sintesi di fine mese che racconta cosa è cambiato e perché.',
      },
      {
        title: 'Suggerimenti mirati',
        description: 'Spese ricorrenti che crescono, duplicati, occasioni di risparmio concrete.',
      },
      {
        title: 'Ricerca per significato',
        description: 'Trovare movimenti anche senza ricordare il nome esatto del beneficiario.',
      },
    ],
    roadmap: [
      {
        title: 'Voce di navigazione',
        detail: 'La sezione è raggiungibile e pronta ad accogliere le pagine.',
        done: true,
      },
      {
        title: 'Aggregati interrogabili',
        detail: 'La porta analytics espone già i riepiloghi su cui poggeranno le risposte.',
        done: true,
      },
      {
        title: 'Motore di risposta',
        detail: 'Traduzione delle domande in query sul dominio, con citazione dei movimenti.',
        done: false,
      },
      SHARED_BACKEND_STEP,
    ],
  },
};
