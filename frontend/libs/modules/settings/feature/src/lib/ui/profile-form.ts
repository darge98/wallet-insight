import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import {
  FormField,
  FormRoot,
  ReadonlyFieldState,
  email,
  form,
  maxLength,
  required,
  schema,
} from '@angular/forms/signals';

import {
  DASHBOARD_PERIOD_PRESETS,
  DashboardPeriodPreset,
  PERIOD_PRESET_LABELS,
  supportedTimeZones,
} from '@wallet/shared-util';
import { SegmentedControl, SegmentedOption } from '@wallet/shared-ui';
import {
  USER_LANGUAGE_LABELS,
  USER_LANGUAGES,
  UserLanguage,
  UserProfile,
} from '@wallet/user-domain';

/** Gli stessi limiti che `User` applica nel backend: rifiutare prima evita un giro di rete. */
const MAX_NAME_LENGTH = 80;
const MAX_EMAIL_LENGTH = 160;

interface ProfileModel {
  firstName: string;
  lastName: string;
  email: string;
  timeZone: string;
  language: UserLanguage;
  dashboardPeriod: DashboardPeriodPreset;
}

/**
 * Le regole del profilo.
 *
 * Fuori dal componente perché sono una proprietà del modello, non della
 * schermata: dicono quando un profilo è valido, non come lo si compila. Sono le
 * stesse dell'onboarding, ed è voluto — un profilo non cambia natura a seconda di
 * dove lo si scrive.
 */
const profileSchema = schema<ProfileModel>((path) => {
  required(path.firstName);
  maxLength(path.firstName, MAX_NAME_LENGTH);
  maxLength(path.lastName, MAX_NAME_LENGTH);
  email(path.email);
  maxLength(path.email, MAX_EMAIL_LENGTH);
  required(path.timeZone);
});

const LANGUAGE_OPTIONS: readonly SegmentedOption<UserLanguage>[] = USER_LANGUAGES.map(
  (language) => ({ value: language, label: USER_LANGUAGE_LABELS[language] }),
);

/**
 * Il profilo, modificabile.
 *
 * Emette il profilo completo e non i singoli campi: l'API lo sostituisce per
 * intero, e chi guarda la schermata vede esattamente ciò che sta per salvare.
 * Cognome ed email vuoti tornano `null`, perché nel dominio l'assenza è `null` e
 * una stringa vuota sarebbe un valore diverso da «non l'ho messo».
 */
@Component({
  selector: 'app-profile-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, FormRoot, SegmentedControl],
  host: { class: 'block' },
  template: `
    <form [formRoot]="profileForm" class="grid gap-4">
      <div class="grid gap-4 sm:grid-cols-2">
        <div>
          <label class="block text-xs font-medium text-ink-muted" for="settings-first-name">
            Nome
          </label>
          <input
            id="settings-first-name"
            type="text"
            autocomplete="given-name"
            [formField]="profileForm.firstName"
            [attr.aria-invalid]="shows(profileForm.firstName())"
            class="field mt-1.5"
          />
          @if (shows(profileForm.firstName())) {
            <p class="mt-1.5 text-xs text-negative">Campo obbligatorio.</p>
          }
        </div>

        <div>
          <label class="block text-xs font-medium text-ink-muted" for="settings-last-name">
            Cognome <span class="text-ink-faint">(facoltativo)</span>
          </label>
          <input
            id="settings-last-name"
            type="text"
            autocomplete="family-name"
            [formField]="profileForm.lastName"
            class="field mt-1.5"
          />
        </div>
      </div>

      <div>
        <label class="block text-xs font-medium text-ink-muted" for="settings-email">
          Email <span class="text-ink-faint">(facoltativa)</span>
        </label>
        <input
          id="settings-email"
          type="email"
          autocomplete="email"
          [formField]="profileForm.email"
          [attr.aria-invalid]="shows(profileForm.email())"
          class="field mt-1.5"
        />
        @if (shows(profileForm.email())) {
          <p class="mt-1.5 text-xs text-negative">Indirizzo non valido.</p>
        }
      </div>

      <div class="grid gap-4 sm:grid-cols-2">
        <div>
          <label class="block text-xs font-medium text-ink-muted" for="settings-time-zone">
            Fuso orario
          </label>
          <select id="settings-time-zone" [formField]="profileForm.timeZone" class="field mt-1.5">
            @for (zone of timeZones; track zone) {
              <option [value]="zone">{{ zone }}</option>
            }
          </select>
        </div>

        <div>
          <label class="block text-xs font-medium text-ink-muted" for="settings-period">
            Periodo predefinito
          </label>
          <select
            id="settings-period"
            [formField]="profileForm.dashboardPeriod"
            class="field mt-1.5"
          >
            @for (preset of periods; track preset) {
              <option [value]="preset">{{ periodLabels[preset] }}</option>
            }
          </select>
        </div>
      </div>

      <div>
        <p class="mb-1.5 text-xs font-medium text-ink-muted">Lingua</p>
        <app-segmented-control
          label="Lingua dell'interfaccia"
          [options]="languages"
          [value]="profileForm.language().value()"
          (valueChange)="profileForm.language().value.set($event)"
        />
      </div>

      <div class="flex items-center gap-3 pt-1">
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          {{ saving() ? 'Salvataggio…' : 'Salva profilo' }}
        </button>
        @if (saved() && !dirty()) {
          <span class="text-xs text-positive">Salvato.</span>
        }
      </div>
    </form>
  `,
})
export class ProfileForm {
  readonly profile = input.required<UserProfile>();
  readonly saving = input(false);
  readonly saved = input(false);

  readonly save = output<UserProfile>();

  protected readonly languages = LANGUAGE_OPTIONS;
  protected readonly timeZones = supportedTimeZones();
  protected readonly periods = DASHBOARD_PERIOD_PRESETS;
  protected readonly periodLabels = PERIOD_PRESET_LABELS;

  private readonly model = signal<ProfileModel>(emptyModel());

  protected readonly profileForm = form(this.model, profileSchema, {
    submission: {
      action: () => {
        this.emit();
        return Promise.resolve();
      },
    },
  });

  /** Vero appena si tocca qualcosa: fa sparire il «Salvato» di un salvataggio precedente. */
  protected readonly dirty = computed(() => this.profileForm().dirty());

  constructor() {
    // Il modello segue il profilo che arriva da fuori, compreso quello che torna
    // dal salvataggio: se il server normalizza un valore, in schermata compare
    // quello vero e non quello digitato.
    effect(() => this.model.set(toModel(this.profile())));
  }

  protected shows(state: ReadonlyFieldState<unknown>): boolean {
    return state.invalid() && state.touched();
  }

  private emit(): void {
    const model = this.model();
    this.save.emit({
      ...this.profile(),
      firstName: model.firstName.trim(),
      lastName: blankToNull(model.lastName),
      email: blankToNull(model.email),
      timeZone: model.timeZone,
      language: model.language,
      defaultDashboardPeriod: model.dashboardPeriod,
    });
  }
}

function toModel(profile: UserProfile): ProfileModel {
  return {
    firstName: profile.firstName,
    lastName: profile.lastName ?? '',
    email: profile.email ?? '',
    timeZone: profile.timeZone,
    language: profile.language,
    dashboardPeriod: profile.defaultDashboardPeriod,
  };
}

function emptyModel(): ProfileModel {
  return {
    firstName: '',
    lastName: '',
    email: '',
    timeZone: 'Europe/Rome',
    language: 'it',
    dashboardPeriod: 'current-month',
  };
}

/** Campo lasciato vuoto: nel dominio è `null`, non una stringa vuota. */
function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}
