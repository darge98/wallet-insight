import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  OnDestroy,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {
  FieldTree,
  FormField,
  FormRoot,
  ReadonlyFieldState,
  email,
  form,
  maxLength,
  required,
  schema,
  validate,
} from '@angular/forms/signals';
import { Router } from '@angular/router';

import { IMPORT_SOURCE_CATALOG, ImportSource, isCompactJwt } from '@wallet/ingestion-domain';
import {
  browserTimeZone,
  DASHBOARD_PERIOD_PRESETS,
  DashboardPeriodPreset,
  isSupportedTimeZone,
  supportedTimeZones,
} from '@wallet/shared-util';
import { SegmentedControl, Icon, SegmentedOption, ThemeStore } from '@wallet/shared-ui';
import { CreateUserProfileCommand, UserLanguage } from '@wallet/user-domain';

import { OnboardingFacade } from '../data-access/onboarding.facade';
import { ONBOARDING_COPY } from '../i18n/onboarding-copy';
import { ONBOARDING_STEPS, OnboardingStepId } from '../onboarding-step';
import { ImportSourceOption } from '../ui/import-source-option';
import { WizardStep, WizardSteps } from '../ui/wizard-steps';

const LANGUAGE_OPTIONS: readonly SegmentedOption<UserLanguage>[] = [
  { value: 'it', label: 'Italiano' },
  { value: 'en', label: 'English' },
];

/** Gli stessi limiti che `User` applica nel backend: rifiutare prima evita un giro di rete. */
const MAX_NAME_LENGTH = 80;
const MAX_EMAIL_LENGTH = 160;

/**
 * La scelta fatta al secondo passo.
 *
 * `later` non è una sorgente: è la decisione esplicita di rimandare. Averla fra
 * le opzioni, invece che come scorciatoia in un angolo, evita che rimandare
 * sembri un errore.
 */
type ImportChoice = ImportSource | 'later';

interface ProfileModel {
  firstName: string;
  lastName: string;
  email: string;
  timeZone: string;
  dashboardPeriod: DashboardPeriodPreset;
}

interface ImportModel {
  choice: ImportChoice | null;
  token: string;
}

/**
 * Le regole del primo passo.
 *
 * Stanno fuori dal componente perché sono una proprietà del modello, non della
 * schermata: descrivono quando un profilo è valido, non come lo si compila.
 */
const profileSchema = schema<ProfileModel>((path) => {
  required(path.firstName);
  maxLength(path.firstName, MAX_NAME_LENGTH);
  maxLength(path.lastName, MAX_NAME_LENGTH);
  email(path.email);
  maxLength(path.email, MAX_EMAIL_LENGTH);
  required(path.timeZone);
});

const importSchema = schema<ImportModel>((path) => {
  required(path.choice);

  // Il token serve solo a BudgetBakers: per le altre scelte il campo non è
  // neppure a schermo, e pretenderlo bloccherebbe chi ha scelto di rimandare.
  validate(path.token, ({ value, valueOf }) => {
    if (valueOf(path.choice) !== 'budget-bakers') return undefined;

    const token = value().trim();
    if (token === '') return { kind: 'required' };
    return isCompactJwt(token) ? undefined : { kind: 'jwt' };
  });
});

/**
 * Pagina di primo accesso, in due passi: profilo e origine dei dati.
 *
 * Vive fuori dalla shell: non c'è ancora un profilo, quindi niente sidebar né
 * navigazione. Ogni passo è un form a signal con il proprio modello e il
 * proprio `submission`: è `[formRoot]` a intercettare l'invio, a marcare i
 * campi come toccati e a eseguire l'azione solo quando il passo è valido.
 *
 * I due passi si consegnano insieme: è il backend a creare profilo e sorgente
 * in una transazione sola.
 */
@Component({
  selector: 'app-onboarding-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, FormRoot, SegmentedControl, Icon, WizardSteps, ImportSourceOption],
  templateUrl: './onboarding-page.html',
  host: { class: 'block min-h-dvh bg-bg text-ink' },
  providers: [OnboardingFacade],
})
export class OnboardingPage implements OnDestroy {
  private readonly onboarding = inject(OnboardingFacade);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  /**
   * L'onboarding vive fuori dalla shell, che è l'unico punto in cui il tema
   * veniva applicato: iniettarlo qui garantisce che anche questa schermata
   * rispetti la preferenza chiaro/scuro.
   */
  protected readonly theme = inject(ThemeStore);

  protected readonly languages = LANGUAGE_OPTIONS;
  protected readonly timeZones = supportedTimeZones();
  protected readonly periods = DASHBOARD_PERIOD_PRESETS;
  protected readonly sources = IMPORT_SOURCE_CATALOG;

  protected readonly language = signal<UserLanguage>(browserLanguage());
  protected readonly step = signal<OnboardingStepId>('profile');
  protected readonly failure = this.onboarding.failure;

  protected readonly copy = computed(() => ONBOARDING_COPY[this.language()]);
  protected readonly stepCopy = computed(() => this.copy().steps[this.step()]);
  protected readonly totalSteps = ONBOARDING_STEPS.length;
  protected readonly stepPosition = computed(() => ONBOARDING_STEPS.indexOf(this.step()) + 1);

  protected readonly wizardSteps = computed<readonly WizardStep[]>(() =>
    ONBOARDING_STEPS.map((id) => ({
      id,
      title: this.copy().steps[id].navTitle,
    })),
  );

  /**
   * Il modello è la fonte di verità, non il DOM.
   *
   * È la differenza che conta in un wizard: i campi del primo passo escono di
   * scena quando si va avanti, ma valori e validità restano qui, intatti.
   */
  private readonly profileModel = signal<ProfileModel>({
    firstName: '',
    lastName: '',
    email: '',
    timeZone: browserTimeZone(),
    dashboardPeriod: 'current-month',
  });

  private readonly importModel = signal<ImportModel>({ choice: null, token: '' });

  protected readonly profileForm = form(this.profileModel, profileSchema, {
    submission: {
      action: async () => {
        this.step.set('import');
      },
      onInvalid: (field) => focusFirstError(field),
    },
  });

  protected readonly importForm = form(this.importModel, importSchema, {
    submission: {
      action: () => this.complete(),
      onInvalid: (field) => focusFirstError(field),
    },
  });

  /** Vero mentre l'azione di invio è in corso: lo tiene il form, non un flag a parte. */
  protected readonly saving = computed(() => this.importForm().submitting());

  /**
   * Cosa dice il pulsante mentre si aspetta.
   *
   * Chi ha collegato una sorgente non sta aspettando un salvataggio: il server
   * sta leggendo i movimenti dalla sorgente, e sono secondi. Un
   * «Salvataggio…» che dura tre secondi sembra un'applicazione bloccata.
   */
  protected readonly submittingLabel = computed(() =>
    this.importForm.choice().value() === 'budget-bakers'
      ? this.copy().importing
      : this.copy().submitting,
  );

  constructor() {
    effect(() => {
      this.document.documentElement.lang = this.language();
    });
  }

  ngOnDestroy(): void {
    // L'applicazione è in italiano: la schermata bilingue non deve lasciare
    // l'attributo `lang` alterato una volta entrati nella dashboard.
    this.document.documentElement.lang = 'it';
  }

  protected setLanguage(language: UserLanguage): void {
    this.language.set(language);
  }

  /** Un errore si mostra quando c'è *e* l'utente è passato di lì: l'invio marca tutto. */
  protected shows(state: ReadonlyFieldState<unknown>): boolean {
    return state.invalid() && state.touched();
  }

  protected tokenError(): 'required' | 'format' | null {
    const state = this.importForm.token();
    if (!state.touched()) return null;
    if (state.getError('required')) return 'required';
    return state.getError('jwt') ? 'format' : null;
  }

  /** Etichetta accanto al nome della sorgente: per ora solo la non disponibilità. */
  protected sourceTag(available: boolean): string | null {
    return available ? null : this.copy().comingSoonTag;
  }

  protected choose(choice: ImportChoice): void {
    this.importForm.choice().value.set(choice);
  }

  protected goToProfile(): void {
    this.step.set('profile');
  }

  /**
   * Consegna dei due passi in una chiamata sola.
   *
   * Profilo e sorgente sono due scritture ma un gesto solo: farle insieme è ciò
   * che permette al backend di eseguirle in transazione, e quindi di non lasciare
   * mai un profilo senza la sorgente che l'utente ha appena scelto.
   *
   * La stessa chiamata porta dentro anche i movimenti della sorgente, quindi
   * quando si collega una sorgente dura secondi e non istanti: è il prezzo per
   * arrivare in panoramica con i propri numeri invece che su una schermata vuota
   * da aggiornare a mano. Se l'import non riesce il primo accesso riesce
   * comunque — il profilo c'è, i dati arriveranno col giro della notte.
   */
  private async complete(): Promise<void> {
    // Il primo passo non è più a schermo ma il suo modello sì: se nel frattempo
    // fosse tornato invalido, si torna dov'è possibile correggerlo.
    if (this.profileForm().invalid()) {
      this.goToProfile();
      return;
    }

    const { choice, token } = this.importModel();
    if (choice === null) return;

    const completed = await this.onboarding.complete({
      profile: this.profileCommand(),
      importConnection: choice === 'budget-bakers' ? { source: choice, token: token.trim() } : null,
    });

    if (completed) {
      await this.router.navigateByUrl('/panoramica', { replaceUrl: true });
    }
  }

  private profileCommand(): CreateUserProfileCommand {
    const value = this.profileModel();
    const address = value.email.trim();

    return {
      firstName: value.firstName.trim(),
      lastName: blankToNull(value.lastName),
      email: address === '' ? null : address.toLowerCase(),
      timeZone: isSupportedTimeZone(value.timeZone) ? value.timeZone : browserTimeZone(),
      language: this.language(),
      defaultDashboardPeriod: value.dashboardPeriod,
    };
  }
}

/**
 * Porta il fuoco sul primo campo che non va.
 *
 * Gli errori portano con sé il controllo a cui sono legati, quindi non serve
 * cercarlo nel DOM: se l'errore non appartiene a nessun campo a schermo — la
 * scelta della sorgente, che non è un `input` — non si sposta nulla.
 */
function focusFirstError(field: FieldTree<unknown>): void {
  field().errorSummary().at(0)?.formField?.focus();
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

function browserLanguage(): UserLanguage {
  const tag = typeof navigator === 'undefined' ? 'it' : navigator.language;
  return tag.toLowerCase().startsWith('en') ? 'en' : 'it';
}
