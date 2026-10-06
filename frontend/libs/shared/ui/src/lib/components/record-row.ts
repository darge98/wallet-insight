import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import {
  awaitingSettlement,
  Category,
  FinanceRecord,
  recordDetail,
  recordTitle,
} from '@wallet/shared-domain';
import { DayLabelPipe } from '../pipes/day-label.pipe';
import { MoneyPipe } from '../pipes/money.pipe';

/**
 * Riga di un movimento: data, chi c'era dall'altra parte, descrizione,
 * categoria, importo.
 *
 * Il titolo è la controparte, e la descrizione solo se quella manca: sui dati
 * reali la controparte c'è su due movimenti su tre ed è un nome che si riconosce
 * a colpo d'occhio — Conad, Amazon — mentre la descrizione senza controparte è
 * spesso il tracciato che ha generato la banca. Quando ci sono entrambe la
 * descrizione ha una colonna sua, troncata, col testo intero nel tooltip.
 *
 * Nessuna icona e nessun divisore, come nel concept: le colonne e lo spazio
 * bastano a separare le righe.
 *
 * Il passaggio da riga impilata a colonne segue la larghezza della riga, non
 * della finestra: data, categoria, importo e spazi fissi valgono fino a 400 px,
 * e la lista Movimenti a 1280 px di finestra ne è larga 616. Sotto `@2xl`
 * (672 px) titolo e «data · categoria · descrizione» si impilano.
 */
@Component({
  selector: 'app-record-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MoneyPipe, DayLabelPipe],
  host: { class: 'block @container' },
  template: `
    <div class="flex items-baseline gap-3 py-2.5 @2xl:gap-4">
      @if (showDate()) {
        <span class="tnum hidden w-12 shrink-0 text-xs text-ink-muted @2xl:block">
          {{ record().date | dayLabel: 'compact' }}
        </span>
      }

      @if (accountName(); as name) {
        <span
          class="h-3.5 w-0.5 shrink-0 self-center rounded-full"
          [style.background-color]="accountColor() ?? 'var(--color-line)'"
          [title]="'Conto: ' + name"
          aria-hidden="true"
        ></span>
        <span class="sr-only">Conto: {{ name }}</span>
      }

      <div
        class="flex min-w-0 flex-1 flex-col gap-0.5 @2xl:flex-row @2xl:items-baseline @2xl:gap-4"
      >
        <span class="flex min-w-0 items-baseline gap-2 @2xl:flex-1">
          <span
            class="truncate text-sm"
            [class]="pending() ? 'text-ink-muted' : 'text-ink'"
            [title]="title()"
          >
            {{ title() }}
          </span>
          <!--
            Il grigio da solo non basterebbe: il colore non è mai l'unico canale, e
            «più chiaro» non dice da sé che cosa significhi. La parola lo dice.
          -->
          @if (pending()) {
            <span class="shrink-0 text-xs whitespace-nowrap text-ink-faint">in attesa</span>
          }
        </span>
        <span
          class="hidden min-w-0 flex-1 truncate text-sm text-ink-muted @2xl:block"
          [title]="detail() ?? ''"
        >
          {{ detail() }}
        </span>
        <!-- Impilata, questa è la seconda riga e accoglie data e descrizione. -->
        <span class="truncate text-xs text-ink-muted @2xl:w-44 @2xl:shrink-0 @2xl:text-sm">
          @if (showDate()) {
            <span class="tnum @2xl:hidden">{{ record().date | dayLabel: 'compact' }} · </span>
          }
          {{ category()?.name ?? 'Senza categoria' }}
          @if (detail(); as text) {
            <span class="@2xl:hidden"> · {{ text }}</span>
          }
        </span>
      </div>

      <span class="tnum w-24 shrink-0 text-right text-sm @2xl:w-28" [class]="amountTone()">
        {{ displayAmount() | money: 'signed' }}
      </span>
    </div>
  `,
})
export class RecordRow {
  readonly record = input.required<FinanceRecord>();
  readonly category = input<Category | null>(null);
  /** Nella lista raggruppata per giorno la data è già nel titolo del gruppo. */
  readonly showDate = input(true);

  /**
   * Il conto da cui il movimento arriva, come nome e come colore.
   *
   * Sono due stringhe e non un `Account` perché questa libreria è condivisa e non
   * può nominare l'area `accounts`: le serve quel tanto che basta a disegnare, non
   * l'entità.
   *
   * Il nome comanda la comparsa della barra: dove non viene passato — la lista
   * della Panoramica — la riga resta esattamente com'era, senza un segno in più
   * che non direbbe niente.
   */
  readonly accountName = input<string | null>(null);

  /**
   * `null` non è un colore mancante per errore: significa che l'utente non ne ha
   * scelto uno, e allora la barra prende la tinta dei bordi. Assegnargliene uno
   * d'ufficio lo renderebbe indistinguibile da una scelta vera — è la stessa
   * regola con cui il colore è nato nel database.
   *
   * Un segno neutro e non l'assenza del segno: così i titoli di righe con e senza
   * colore restano incolonnati, e l'elenco non sembra sfilacciato.
   */
  readonly accountColor = input<string | null>(null);

  protected readonly title = computed(() => recordTitle(this.record()));

  protected readonly detail = computed(() => recordDetail(this.record()));

  /** L'importo arriva già col segno: non c'è niente da ricavare dal tipo. */
  protected readonly displayAmount = computed(() => this.record().amount);

  /** Un movimento che la banca deve ancora confermare: si vede, ma è provvisorio. */
  protected readonly pending = computed(() => awaitingSettlement(this.record()));

  protected readonly amountTone = computed(() => {
    // Anche l'importo si smorza: è la cifra che può ancora cambiare.
    if (this.pending()) {
      return 'text-ink-muted';
    }

    switch (this.record().type) {
      case 'income':
        return 'text-accent';
      case 'transfer':
        return 'text-ink-muted';
      case 'expense':
        return 'text-ink';
    }
  });
}
