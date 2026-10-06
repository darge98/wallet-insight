import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { FormField, FormRoot, form, schema, validate } from '@angular/forms/signals';

import { Account } from '@wallet/accounts-domain';
import {
  asAccountId,
  asCategoryId,
  Category,
  categoryTree,
  IsoDate,
  money,
} from '@wallet/shared-domain';
import { amountInput, Icon, parseAmount } from '@wallet/shared-ui';
import {
  Cadence,
  CADENCE_UNITS,
  CadenceUnit,
  cadenceUnitLabel,
  MAX_CADENCE_EVERY,
  Subscription,
  SubscriptionDraft,
} from '@wallet/subscriptions-domain';

type Frequency = 'monthly' | 'quarterly' | 'semiannual' | 'yearly' | 'weekly' | 'custom';

interface SubscriptionFormModel {
  name: string;
  amount: string;
  frequency: Frequency;
  every: string;
  unit: CadenceUnit;
  startDate: string;
  endDate: string;
  /** Vuoto vuol dire «nessuna», come in `accountId`. */
  categoryId: string;
  accountId: string;
}

/** Le cadenze più comuni; tutte le altre passano da «Altra cadenza». */
const PRESETS: readonly {
  readonly value: Exclude<Frequency, 'custom'>;
  readonly label: string;
  readonly cadence: Cadence;
}[] = [
  { value: 'monthly', label: 'Ogni mese', cadence: { every: 1, unit: 'month' } },
  { value: 'quarterly', label: 'Ogni 3 mesi', cadence: { every: 3, unit: 'month' } },
  { value: 'semiannual', label: 'Ogni 6 mesi', cadence: { every: 6, unit: 'month' } },
  { value: 'yearly', label: 'Ogni anno', cadence: { every: 1, unit: 'year' } },
  { value: 'weekly', label: 'Ogni settimana', cadence: { every: 1, unit: 'week' } },
];

/** Come `Subscription.MAX_NAME_LENGTH` nel backend. */
const NAME_MAX_LENGTH = 80;

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function parseEvery(raw: string): number | null {
  const every = Number(raw.trim());
  return Number.isInteger(every) && every >= 1 && every <= MAX_CADENCE_EVERY ? every : null;
}

const subscriptionSchema = schema<SubscriptionFormModel>((path) => {
  validate(path.name, ({ value }) => {
    const name = value().trim();
    if (name === '') return { kind: 'required' };
    return name.length > NAME_MAX_LENGTH ? { kind: 'too-long' } : undefined;
  });
  validate(path.amount, ({ value }) =>
    parseAmount(value()) === null ? { kind: 'amount' } : undefined,
  );
  validate(path.every, ({ value, valueOf }) =>
    valueOf(path.frequency) === 'custom' && parseEvery(value()) === null
      ? { kind: 'every' }
      : undefined,
  );
  validate(path.startDate, ({ value }) =>
    ISO_DATE.test(value()) ? undefined : { kind: 'required' },
  );
  // Le date ISO si confrontano come stringhe: l'ordine alfabetico è quello del calendario.
  validate(path.endDate, ({ value, valueOf }) =>
    value() !== '' && value() < valueOf(path.startDate) ? { kind: 'before-start' } : undefined,
  );
});

function emptyModel(today: IsoDate): SubscriptionFormModel {
  return {
    name: '',
    amount: '',
    frequency: 'monthly',
    every: '1',
    unit: 'month',
    startDate: today,
    endDate: '',
    categoryId: '',
    accountId: '',
  };
}

function modelOf(draft: SubscriptionDraft): SubscriptionFormModel {
  return {
    name: draft.name,
    amount: draft.amount.amount > 0 ? amountInput(draft.amount.amount) : '',
    frequency: frequencyOf(draft.cadence),
    every: String(draft.cadence.every),
    unit: draft.cadence.unit,
    startDate: draft.startDate,
    endDate: draft.endDate ?? '',
    categoryId: draft.categoryId ?? '',
    accountId: draft.accountId ?? '',
  };
}

function frequencyOf(cadence: Cadence): Frequency {
  return (
    PRESETS.find(
      (preset) => preset.cadence.every === cadence.every && preset.cadence.unit === cadence.unit,
    )?.value ?? 'custom'
  );
}

/**
 * Il pannello con cui si crea o si modifica un abbonamento.
 *
 * La fine è facoltativa ed è il modo di disdire: l'abbonamento resta nell'elenco,
 * fra i conclusi, invece di sparire insieme alla sua storia.
 */
@Component({
  selector: 'app-subscription-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, FormRoot, Icon],
  host: { class: 'contents' },
  templateUrl: './subscription-editor.html',
})
export class SubscriptionEditor {
  readonly open = input(false);
  /** `null` vuol dire «nuovo». */
  readonly subscription = input<Subscription | null>(null);
  /** Per un nuovo abbonamento: i valori da cui partire, per esempio quelli di un movimento. */
  readonly prefill = input<SubscriptionDraft | null>(null);
  readonly today = input.required<IsoDate>();
  readonly categories = input<readonly Category[]>([]);
  /** I conti fra cui scegliere; quello già scelto resta anche se archiviato. */
  readonly accounts = input<readonly Account[]>([]);
  readonly saving = input(false);
  readonly error = input<string | null>(null);

  readonly closed = output<void>();
  readonly saved = output<SubscriptionDraft>();
  readonly removed = output<void>();

  protected readonly presets = PRESETS;
  protected readonly units = CADENCE_UNITS;

  protected readonly model = signal<SubscriptionFormModel>(emptyModel(''));

  protected readonly subscriptionForm = form(this.model, subscriptionSchema, {
    submission: {
      action: async () => {
        const { name, amount, startDate, endDate, categoryId, accountId } = this.model();
        this.saved.emit({
          name: name.trim(),
          amount: money(parseAmount(amount) ?? 0),
          cadence: this.cadence(),
          startDate,
          endDate: endDate === '' ? null : endDate,
          categoryId: categoryId === '' ? null : asCategoryId(categoryId),
          accountId: accountId === '' ? null : asAccountId(accountId),
        });
      },
    },
  });

  protected readonly attempted = signal(false);
  protected readonly confirmingDelete = signal(false);

  constructor() {
    effect(() => {
      // Anche «open»: riaprire «nuovo» due volte di fila deve ripartire da un modulo vuoto.
      this.open();
      const draft = this.subscription() ?? this.prefill();
      this.model.set(
        draft
          ? modelOf(draft)
          : // Senza dipendenza: il modulo non deve ripartire se cambia la data di oggi.
            emptyModel(untracked(() => this.today())),
      );
      this.subscriptionForm().reset();
      this.attempted.set(false);
      this.confirmingDelete.set(false);
    });
  }

  protected readonly title = computed(() => {
    const subscription = this.subscription();
    return subscription ? `Modifica «${subscription.name}»` : 'Nuovo abbonamento';
  });

  /** Le macro come gruppi, le sottocategorie come scelte: i movimenti stanno lì. */
  protected readonly categoryGroups = computed(() =>
    categoryTree(this.categories()).filter((branch) => branch.children.length > 0),
  );

  protected readonly isCustom = computed(() => this.model().frequency === 'custom');

  protected unitLabel(unit: CadenceUnit): string {
    return cadenceUnitLabel(unit, parseEvery(this.model().every) ?? 2);
  }

  protected showError(field: 'name' | 'amount' | 'every' | 'startDate' | 'endDate'): boolean {
    const state = this.subscriptionForm[field]();
    return state.invalid() && (state.touched() || this.attempted());
  }

  private cadence(): Cadence {
    const { frequency, every, unit } = this.model();
    const preset = PRESETS.find((candidate) => candidate.value === frequency);
    return preset ? preset.cadence : { every: parseEvery(every) ?? 1, unit };
  }
}
