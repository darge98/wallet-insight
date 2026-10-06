import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { FormField, FormRoot, form } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';

import { Account } from '@wallet/accounts-domain';
import {
  awaitingSettlement,
  Category,
  categoryTree,
  COUNTER_PARTY_LABELS,
  editOf,
  FinanceRecord,
  NO_CATEGORY,
  RecordEdit,
  RECORD_STATE_LABELS,
  RECORD_TYPE_LABELS,
  recordTitle,
} from '@wallet/shared-domain';
import { Badge, DayLabelPipe, Icon, MoneyPipe } from '@wallet/shared-ui';

interface DetailRow {
  readonly label: string;
  readonly value: string;
}

/** Le categorie di un gruppo, per un `<optgroup>`. */
interface CategoryGroup {
  readonly label: string;
  readonly categories: readonly Category[];
}

/**
 * Pannello laterale del movimento, con le tre cose che l'utente può correggere.
 *
 * Le tre sono un solo modulo con un solo salvataggio, e non tre campi che si
 * salvano da soli: sono la stessa frase detta in tre pezzi — chi era, cos'era,
 * di che tipo — e correggerne una sola quasi sempre vuol dire correggere anche
 * le altre.
 *
 * Da qui non si vede da dove i dati sono arrivati, e non è una semplificazione:
 * l'interfaccia non ne sa niente. Un movimento ha una descrizione, una
 * controparte e una categoria — quelle che si leggono nei campi — e salvando si
 * mandano quelle. Che il backend tenga da parte i valori con cui il movimento è
 * stato importato è una sua garanzia, non una cosa da amministrare di qui.
 */
@Component({
  selector: 'app-record-detail-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge, Icon, MoneyPipe, DayLabelPipe, FormField, FormRoot, RouterLink],
  host: { class: 'contents' },
  templateUrl: './record-detail-panel.html',
})
export class RecordDetailPanel {
  readonly record = input.required<FinanceRecord | null>();
  readonly category = input<Category | null>(null);
  readonly account = input<Account | null>(null);
  readonly categories = input<readonly Category[]>([]);
  readonly saving = input(false);
  readonly saveFailed = input(false);
  /** Da questo movimento è appena nato un abbonamento. */
  readonly subscribed = input(false);

  readonly closed = output<void>();
  readonly saved = output<RecordEdit>();
  /** L'utente vuole registrare questa uscita come abbonamento. */
  readonly subscribe = output<void>();

  protected readonly model = signal<RecordEdit>({
    description: '',
    counterParty: '',
    category: NO_CATEGORY,
  });

  protected readonly editForm = form(this.model, {
    submission: { action: async () => this.saved.emit(this.model()) },
  });

  /** Finché non si tocca niente non c'è niente da salvare: il bottone lo dice. */
  protected readonly dirty = computed(() => this.editForm().dirty());

  protected readonly noCategory = NO_CATEGORY;

  /**
   * Un movimento che la banca deve ancora confermare non si corregge, e i campi
   * lo dicono da soli restando spenti. Quando sarà contabilizzato la sorgente lo
   * avrà sostituito con un altro, e quello che ci si fosse scritto sarebbe finito
   * su una riga destinata a sparire.
   */
  protected readonly pending = computed(() => {
    const record = this.record();
    return record !== null && awaitingSettlement(record);
  });

  constructor() {
    // Il modulo riparte da capo a ogni movimento aperto, e anche dopo un
    // salvataggio: ciò che si vede è sempre la risposta del server, non la
    // bozza appena digitata.
    effect(() => {
      const record = this.record();
      this.model.set(
        record ? editOf(record) : { description: '', counterParty: '', category: NO_CATEGORY },
      );
      this.editForm().reset();
    });
  }

  protected readonly title = computed(() => {
    const record = this.record();
    return record ? recordTitle(record) : '';
  });

  protected readonly typeLabel = computed(() => {
    const record = this.record();
    return record ? RECORD_TYPE_LABELS[record.type] : '';
  });

  /** «Pagato a» o «Ricevuto da»: dipende da che verso ha il movimento. */
  protected readonly counterPartyLabel = computed(() => {
    const record = this.record();
    return record ? COUNTER_PARTY_LABELS[record.type] : COUNTER_PARTY_LABELS.transfer;
  });

  /**
   * Un movimento senza categoria esiste nel modello ma non in questi dati: su
   * 1678 sono zero. La voce vuota compare solo se capita, e resta un fatto e non
   * una scelta — l'API non sa togliere una categoria, sa solo spostarla.
   */
  protected readonly withoutCategory = computed(() => this.record()?.categoryId === null);

  /** Le sottocategorie sotto la loro macro: un movimento non sta in una macro. */
  protected readonly categoryGroups = computed<readonly CategoryGroup[]>(() =>
    categoryTree(this.categories())
      .filter((branch) => branch.children.length > 0)
      .map((branch) => ({ label: branch.macro.name, categories: branch.children })),
  );

  protected readonly amountTone = computed(() => {
    switch (this.record()?.type) {
      case 'income':
        return 'text-accent';
      case 'transfer':
        return 'text-ink-muted';
      default:
        return 'text-ink';
    }
  });

  /** Ciò che non si corregge da qui: sono fatti avvenuti altrove. */
  protected readonly rows = computed<readonly DetailRow[]>(() => {
    const record = this.record();
    if (!record) return [];

    return [
      { label: 'Conto', value: this.account()?.name ?? '—' },
      { label: 'Stato', value: RECORD_STATE_LABELS[record.state] },
    ];
  });
}
