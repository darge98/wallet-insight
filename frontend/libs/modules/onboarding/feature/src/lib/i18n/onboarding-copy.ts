import { ImportSource } from '@wallet/ingestion-domain';
import { DashboardPeriodPreset } from '@wallet/shared-util';
import { UserLanguage } from '@wallet/user-domain';

import { OnboardingStepId } from '../onboarding-step';

/**
 * Testi dell'onboarding, nelle due lingue offerte in questa schermata.
 *
 * È un dizionario tipizzato interno alla feature: l'applicazione non ha ancora
 * un sistema i18n globale, e introdurne uno adesso — con formattazione, titoli
 * di rotta e testi di tutte le sezioni — sarebbe un lavoro molto più grande
 * dell'onboarding stesso. La lingua scelta viene comunque salvata nel profilo,
 * pronta per la localizzazione completa.
 */
export interface OnboardingStepCopy {
  /** Testo nell'indicatore dei passi: deve stare su una riga. */
  readonly navTitle: string;
  /** Titolo della schermata quando il passo è quello corrente. */
  readonly title: string;
}

export interface OnboardingCopy {
  readonly eyebrow: string;
  readonly stepsLabel: string;
  readonly steps: Readonly<Record<OnboardingStepId, OnboardingStepCopy>>;

  readonly identityHeading: string;
  readonly firstNameLabel: string;
  readonly lastNameLabel: string;
  readonly optionalTag: string;
  readonly emailLabel: string;
  readonly emailPlaceholder: string;

  readonly preferencesHeading: string;
  readonly timeZoneLabel: string;
  readonly languageLabel: string;
  readonly themeToggle: string;
  readonly periodLabel: string;
  readonly periodLabels: Readonly<Record<DashboardPeriodPreset, string>>;

  readonly importHeading: string;
  readonly sourcesLabel: string;
  readonly sourceDescriptions: Readonly<Record<ImportSource, string>>;
  readonly laterName: string;
  readonly laterDescription: string;
  readonly comingSoonTag: string;
  readonly tokenLabel: string;
  readonly tokenPlaceholder: string;
  readonly tokenHint: string;
  readonly tokenRequiredError: string;
  readonly tokenFormatError: string;
  readonly choiceRequiredError: string;

  readonly back: string;
  readonly continueLabel: string;
  readonly submit: string;
  readonly submitting: string;
  /**
   * L'attesa quando una sorgente è collegata: non si sta salvando un profilo, si
   * stanno portando dentro i movimenti di una vita, e sono secondi, non istanti.
   * Dire cosa sta succedendo è la differenza fra un'attesa e un dubbio.
   */
  readonly importing: string;
  readonly requiredError: string;
  readonly tooLongError: string;
  readonly emailError: string;
  readonly submitError: string;
  readonly conflictError: string;
  readonly invalidError: string;
}

export const ONBOARDING_COPY: Readonly<Record<UserLanguage, OnboardingCopy>> = {
  it: {
    eyebrow: 'Primo accesso',
    stepsLabel: 'Passi della configurazione',
    steps: {
      profile: {
        navTitle: 'Profilo',
        title: 'Profilo',
      },
      import: {
        navTitle: 'Origine dei dati',
        title: 'Origine dei dati',
      },
    },

    identityHeading: 'Identità',
    firstNameLabel: 'Nome',
    lastNameLabel: 'Cognome',
    optionalTag: 'opzionale',
    emailLabel: 'Email',
    emailPlaceholder: 'nome@esempio.it',

    preferencesHeading: 'Preferenze',
    timeZoneLabel: 'Fuso orario',
    languageLabel: 'Lingua',
    themeToggle: 'Cambia tema',
    periodLabel: 'Periodo predefinito della dashboard',
    periodLabels: {
      today: 'Oggi',
      'current-week': 'Questa settimana',
      'current-month': 'Questo mese',
      'current-year': "Quest'anno",
      'last-7-days': 'Ultimi 7 giorni',
      'last-30-days': 'Ultimi 30 giorni',
    },

    importHeading: 'Importazione',
    sourcesLabel: 'Sorgente dei movimenti',
    sourceDescriptions: {
      'budget-bakers': 'Conti, categorie e movimenti da Wallet, tramite token personale.',
      psd2: 'Collegamento diretto alla banca (Open Banking).',
    },
    laterName: 'Decido più tardi',
    laterDescription: 'Configurabile in seguito dalle Impostazioni.',
    comingSoonTag: 'presto disponibile',
    tokenLabel: 'Token personale',
    tokenPlaceholder: 'eyJhbGciOiJIUzI1NiJ9.…',
    tokenHint: 'Wallet → Impostazioni → API.',
    tokenRequiredError: 'Campo obbligatorio.',
    tokenFormatError: 'Token non valido.',
    choiceRequiredError: 'Seleziona una sorgente.',

    back: 'Indietro',
    continueLabel: 'Continua',
    submit: 'Apri la dashboard',
    submitting: 'Salvataggio…',
    importing: 'Importazione in corso…',
    requiredError: 'Campo obbligatorio.',
    tooLongError: 'Il valore è troppo lungo.',
    emailError: 'Inserisci un indirizzo valido.',
    submitError: 'Operazione non riuscita. Riprova.',
    conflictError: 'Email già associata a un altro profilo.',
    invalidError: 'Dati non validi. Verifica email e token.',
  },
  en: {
    eyebrow: 'First run',
    stepsLabel: 'Setup steps',
    steps: {
      profile: {
        navTitle: 'Profile',
        title: 'Profile',
      },
      import: {
        navTitle: 'Data source',
        title: 'Data source',
      },
    },

    identityHeading: 'Identity',
    firstNameLabel: 'First name',
    lastNameLabel: 'Last name',
    optionalTag: 'optional',
    emailLabel: 'Email',
    emailPlaceholder: 'name@example.com',

    preferencesHeading: 'Preferences',
    timeZoneLabel: 'Time zone',
    languageLabel: 'Language',
    themeToggle: 'Toggle theme',
    periodLabel: 'Default dashboard period',
    periodLabels: {
      today: 'Today',
      'current-week': 'This week',
      'current-month': 'This month',
      'current-year': 'This year',
      'last-7-days': 'Last 7 days',
      'last-30-days': 'Last 30 days',
    },

    importHeading: 'Import',
    sourcesLabel: 'Record source',
    sourceDescriptions: {
      'budget-bakers': 'Accounts, categories and records from Wallet, via personal token.',
      psd2: 'Direct bank connection (Open Banking).',
    },
    laterName: 'I’ll decide later',
    laterDescription: 'Can be configured later in Settings.',
    comingSoonTag: 'coming soon',
    tokenLabel: 'Personal token',
    tokenPlaceholder: 'eyJhbGciOiJIUzI1NiJ9.…',
    tokenHint: 'Wallet → Settings → API.',
    tokenRequiredError: 'This field is required.',
    tokenFormatError: 'Invalid token.',
    choiceRequiredError: 'Select a source.',

    back: 'Back',
    continueLabel: 'Continue',
    submit: 'Open the dashboard',
    submitting: 'Saving…',
    importing: 'Importing…',
    requiredError: 'This field is required.',
    tooLongError: 'This value is too long.',
    emailError: 'Enter a valid email address.',
    submitError: 'Something went wrong. Try again.',
    conflictError: 'Email already used by another profile.',
    invalidError: 'Invalid details. Check email and token.',
  },
};
