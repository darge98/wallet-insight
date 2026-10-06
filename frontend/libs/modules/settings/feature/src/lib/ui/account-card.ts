import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';

import { Account, ACCOUNT_KIND_LABELS, isRenamedByUser } from '@wallet/accounts-domain';
import { Icon, MoneyPipe } from '@wallet/shared-ui';

import { ACCOUNT_COLORS } from './account-colors';

/**
 * Misure della tavolozza, in pixel: quattro colonne da 24 con 8 di distanza e 8
 * di margine interno.
 *
 * Servono *prima* che il pannello compaia — la posizione si calcola su
 * `beforetoggle`, quando è ancora fuori dal flusso e misurarlo darebbe zero — e
 * per questo vivono qui accanto alle classi da cui derivano: cambiare le une
 * senza le altre sposta il pannello.
 */
const PALETTE_WIDTH = 136;
const PALETTE_HEIGHT = 72;
/** Distanza dal pallino e dai bordi della finestra. */
const PALETTE_GAP = 8;

/** Un `id` per ogni carta: `popovertarget` lega invocante e pannello per id. */
let nextPaletteId = 0;

/**
 * Un conto in elenco: nome modificabile, colore, saldo.
 *
 * Il nome si modifica sul posto e si conferma uscendo dal campo o con Invio —
 * niente pulsante "salva". Con Esc si torna al valore di prima: una modifica
 * fatta per sbaglio deve avere una via d'uscita che non sia ricordarsi il nome
 * vecchio. Il campo non manda niente se il valore non è cambiato o è vuoto: un
 * conto senza nome non esiste, e il server lo rifiuterebbe comunque.
 *
 * Quando il nome è stato cambiato dall'utente, sotto compare come si chiama nella
 * sorgente. Non è un dettaglio decorativo: è l'unico modo di ritrovare quel conto
 * nell'applicazione da cui proviene, e di sapere che rimettendo quel nome
 * tornerebbe a seguirla.
 */
@Component({
  selector: 'app-account-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, MoneyPipe],
  host: { class: 'block rounded-card border border-line bg-surface p-4' },
  /*
   * Rete di sicurezza sulla regola di cui sopra: `[popover]:not(:popover-open)`
   * ha specificità (0,2,0) e batte qualunque utility a classe singola. Serve
   * perché il guasto che previene non lo vede nessun test — jsdom non
   * implementa il popover — e si manifesta come "un riquadro fermo in alto a
   * sinistra", che non fa pensare a un `display`.
   */
  styles: `
    [popover]:not(:popover-open) {
      display: none;
    }
  `,
  template: `
    <div class="flex items-start gap-3">
      <button
        #trigger
        type="button"
        class="hit-area mt-1 size-4 shrink-0 cursor-pointer rounded-full border transition-transform hover:scale-110"
        [style.background-color]="account().color ?? 'transparent'"
        [class.border-line]="!account().color"
        [class.border-transparent]="!!account().color"
        [attr.aria-label]="'Scegli un colore per ' + account().name"
        [attr.aria-expanded]="paletteOpen()"
        [attr.popovertarget]="paletteId"
      ></button>

      <div class="min-w-0 flex-1">
        <input
          type="text"
          class="w-full rounded-control border border-transparent bg-transparent px-1.5 py-0.5 text-sm font-medium text-ink transition-colors hover:border-line focus:border-line focus:bg-bg focus:outline-none"
          [value]="draft()"
          [attr.aria-label]="'Nome del conto ' + account().sourceName"
          (input)="draft.set($any($event.target).value)"
          (blur)="commit()"
          (keydown.enter)="commit()"
          (keydown.escape)="reset()"
        />

        <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 px-1.5 text-xs text-ink-muted">
          <span>{{ kindLabel() }}</span>
          @if (account().numberLast4) {
            <span class="tnum text-ink-faint">•• {{ account().numberLast4 }}</span>
          }
          @if (account().archived) {
            <span class="text-ink-faint">Archiviato nella sorgente</span>
          }
        </p>

        @if (renamed()) {
          <p class="mt-0.5 px-1.5 text-xs text-ink-faint">
            Nella sorgente: {{ account().sourceName }}
          </p>
        }
      </div>

      <p class="tnum type-figure-sm shrink-0 text-right text-ink">
        {{ account().balance | money }}
      </p>
    </div>

    <!-- Popover nativo: sta nel top layer, quindi non occupa spazio nella carta e
         non può essere tagliato da nessun contenitore. Si chiude da solo con Esc
         o con un clic fuori, e il fuoco torna al pallino: comportamenti che
         scritti a mano sarebbero tre listener e un test ciascuno.

         Sull'elemento che porta l'attributo popover non va NESSUNA utility che
         imposti display — niente grid, flex o block. A nasconderlo da chiuso è
         una regola dello user agent, [popover]:not(:popover-open){display:none},
         e gli stili d'autore la battono: con una classe grid qui sopra il
         pannello resta visibile per sempre in alto a sinistra e non entra mai
         nel top layer, quindi niente Esc e niente chiusura al clic fuori. La
         griglia sta perciò su un elemento interno. -->
    <div
      #palette
      popover="auto"
      [id]="paletteId"
      class="fixed inset-auto m-0 rounded-card border border-line bg-surface p-2"
      style="box-shadow: 0 8px 24px -14px rgb(9 26 20 / 35%)"
      [style.left.px]="palettePosition().x"
      [style.top.px]="palettePosition().y"
      role="group"
      aria-label="Colori disponibili"
      (beforetoggle)="onPaletteToggle($event)"
    >
      <div class="grid grid-cols-4 gap-2">
        @for (option of colors; track option.value) {
          <button
            type="button"
            class="flex size-6 cursor-pointer items-center justify-center rounded-full transition-transform hover:scale-110"
            [style.background-color]="option.value"
            [attr.aria-label]="option.label"
            [attr.aria-pressed]="account().color === option.value"
            [attr.popovertarget]="paletteId"
            popovertargetaction="hide"
            (click)="choose(option.value)"
          >
            @if (account().color === option.value) {
              <app-icon name="check" [size]="12" class="text-white" />
            }
          </button>
        }
      </div>
    </div>
  `,
})
export class AccountCard {
  readonly account = input.required<Account>();

  readonly renamedTo = output<string>();
  readonly recoloredTo = output<string>();

  protected readonly colors = ACCOUNT_COLORS;
  protected readonly paletteId = `account-palette-${nextPaletteId++}`;

  /** Lo stato lo racconta il browser: qui si rispecchia per `aria-expanded`. */
  protected readonly paletteOpen = signal(false);
  protected readonly palettePosition = signal({ x: 0, y: 0 });

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly paletteRef = viewChild.required<ElementRef<HTMLElement>>('palette');

  /**
   * Il testo nel campo mentre lo si scrive.
   *
   * `linkedSignal` e non una copia fatta una volta: riparte dal nome del conto
   * ogni volta che quello cambia da fuori — cioè quando il server risponde al
   * salvataggio, magari con un valore normalizzato — e nel frattempo resta
   * scrivibile. Un `signal` inizializzato una sola volta mostrerebbe per sempre
   * il nome che il conto aveva al primo disegno.
   */
  protected readonly draft = linkedSignal(() => this.account().name);

  protected readonly kindLabel = computed(() => ACCOUNT_KIND_LABELS[this.account().kind]);
  protected readonly renamed = computed(() => isRenamedByUser(this.account()));

  protected commit(): void {
    const next = this.draft().trim();
    if (next === '' || next === this.account().name) {
      this.reset();
      return;
    }
    this.renamedTo.emit(next);
  }

  protected reset(): void {
    this.draft.set(this.account().name);
  }

  /**
   * Piazza la tavolozza sotto il pallino, appena prima che compaia.
   *
   * Su `beforetoggle` e non su `toggle`: dopo sarebbe già a schermo, e il
   * pannello si vedrebbe saltare dal centro della finestra — dove lo mettono gli
   * stili predefiniti del popover — al suo posto.
   */
  protected onPaletteToggle(event: Event): void {
    const aperto = (event as ToggleEvent).newState === 'open';
    this.paletteOpen.set(aperto);
    if (!aperto) return;

    const dot = this.trigger().nativeElement.getBoundingClientRect();
    const view = this.trigger().nativeElement.ownerDocument.defaultView;

    // La posizione è calcolata una volta sola, e il pannello è `fixed`: se la
    // pagina scorre resterebbe fermo mentre il pallino scivola via. Alla prima
    // rotellina si chiude, invece di inseguire il pallino a ogni pixel.
    // `capture` perché lo scroll non risale, `once` perché si toglie da sé.
    view?.addEventListener('scroll', this.closeOnScroll, { capture: true, once: true });
    const larghezza = view?.innerWidth ?? PALETTE_WIDTH;
    const altezza = view?.innerHeight ?? PALETTE_HEIGHT;

    // Sotto il pallino, a meno che sotto non ci stia: allora sopra.
    const sotto = dot.bottom + PALETTE_GAP;
    const y =
      sotto + PALETTE_HEIGHT + PALETTE_GAP > altezza
        ? dot.top - PALETTE_HEIGHT - PALETTE_GAP
        : sotto;

    // Allineata al pallino, ma senza uscire dalla finestra: su viewport strette
    // il pannello è più largo dello spazio che ha a destra.
    const x = Math.max(PALETTE_GAP, Math.min(dot.left, larghezza - PALETTE_WIDTH - PALETTE_GAP));

    this.palettePosition.set({ x, y });
  }

  /**
   * Chiude la tavolozza se è ancora aperta.
   *
   * La guardia non è prudenza: `hidePopover()` su un pannello già chiuso
   * solleva, e fra l'apertura e questo scroll l'utente può averlo chiuso in
   * tre modi diversi — Esc, un clic fuori, la scelta di un colore.
   */
  private readonly closeOnScroll = (): void => {
    if (this.paletteOpen()) {
      this.paletteRef().nativeElement.hidePopover();
    }
  };

  /** A chiudere la tavolozza dopo una scelta è `popovertargetaction` sul pulsante. */
  protected choose(color: string): void {
    if (color !== this.account().color) {
      this.recoloredTo.emit(color);
    }
  }
}
