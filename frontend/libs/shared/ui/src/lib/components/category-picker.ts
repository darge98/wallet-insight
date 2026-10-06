import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { OverlayModule } from '@angular/cdk/overlay';
import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  linkedSignal,
  model,
  signal,
  viewChild,
} from '@angular/core';
import { FormValueControl } from '@angular/forms/signals';

import { Category, categoryTree } from '@wallet/shared-domain';

import { Icon } from './icon';

interface PickerGroup {
  readonly id: string;
  readonly name: string;
  readonly children: readonly Category[];
}

/** «Caffè» e «caffe» sono la stessa ricerca, come «BAR» e «bar». */
const normalized = (text: string) =>
  text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase('it');

/**
 * La scelta di una sottocategoria, con la ricerca.
 *
 * Le categorie sono un centinaio: scrivendo, l'elenco tiene quelle che contengono
 * il testo, nel nome o in quello della macro («trasp» dà tutti i Trasporti).
 * Tastiera, focus e ARIA sono di `@angular/aria`; qui c'è il filtro.
 *
 * Il valore è l'id della categoria, `''` per nessuna: è un controllo dei signal
 * form (`[formField]`), e fuori da un form si usa con `[(value)]`.
 */
@Component({
  selector: 'app-category-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, OverlayModule, Icon],
  host: { class: 'block' },
  template: `
    <div #origin class="relative">
      <input
        #combobox="ngCombobox"
        ngCombobox
        class="field pr-9"
        autocomplete="off"
        [id]="inputId()"
        [attr.aria-label]="label()"
        [placeholder]="placeholder()"
        [disabled]="disabled()"
        [(value)]="query"
        [(expanded)]="expanded"
        (click)="expanded.set(true)"
      />
      <app-icon
        name="chevron-down"
        [size]="16"
        class="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-ink-faint"
      />
    </div>

    <ng-template
      [cdkConnectedOverlay]="{ origin, usePopover: 'inline', matchWidth: true }"
      [cdkConnectedOverlayOpen]="expanded()"
    >
      <ng-template ngComboboxPopup [combobox]="combobox">
        <div
          class="mt-1 max-h-72 overflow-y-auto rounded-control border border-line bg-surface p-1 shadow-lg"
        >
          <div
            #listbox="ngListbox"
            ngListbox
            ngComboboxWidget
            focusMode="activedescendant"
            selectionMode="explicit"
            class="outline-none"
            [tabindex]="-1"
            [activeDescendant]="listbox.activeDescendant()"
            [(value)]="selection"
            (click)="commit()"
            (keydown.enter)="commit()"
          >
            @if (search() === '' && emptyLabel(); as empty) {
              <div ngOption [value]="''" [label]="empty" class="picker-option">
                {{ empty }}
              </div>
            }
            @for (group of groups(); track group.id) {
              <div role="group" [attr.aria-label]="group.name">
                <div aria-hidden="true" class="px-3 pt-2 pb-1 text-xs font-medium text-ink-faint">
                  {{ group.name }}
                </div>
                @for (category of group.children; track category.id) {
                  <div ngOption [value]="category.id" [label]="category.name" class="picker-option">
                    {{ category.name }}
                  </div>
                }
              </div>
            } @empty {
              <p class="px-3 py-2 text-sm text-ink-muted">Nessuna categoria trovata.</p>
            }
          </div>
        </div>
      </ng-template>
    </ng-template>

    <div aria-live="polite" class="sr-only">
      {{ expanded() && groups().length === 0 ? 'Nessuna categoria trovata.' : '' }}
    </div>
  `,
  styles: `
    .picker-option {
      display: flex;
      align-items: center;
      min-height: 2.5rem;
      padding: 0 0.75rem;
      border-radius: 0.375rem;
      font-size: 0.875rem;
      color: var(--color-ink);
      cursor: pointer;
    }

    .picker-option:hover,
    .picker-option[data-active='true'] {
      background-color: var(--color-soft);
    }

    .picker-option[aria-selected='true'] {
      color: var(--color-accent);
      font-weight: 500;
    }

    @media (min-width: 40rem) {
      .picker-option {
        min-height: 2.25rem;
      }
    }
  `,
})
export class CategoryPicker implements FormValueControl<string> {
  /** L'id della sottocategoria scelta; `''` per nessuna. */
  readonly value = model('');
  readonly disabled = input(false);

  /** Tutte le categorie: si scelgono le sottocategorie, le macro le raggruppano. */
  readonly categories = input.required<readonly Category[]>();
  /** La voce per «nessuna categoria»; senza, non si offre. */
  readonly emptyLabel = input<string | null>(null);
  /** Una categoria da non offrire: quella che si sta unendo a un'altra. */
  readonly exclude = input<string | null>(null);
  readonly inputId = input<string | undefined>(undefined);
  /** Il nome accessibile quando manca un `<label for>`. */
  readonly label = input<string | undefined>(undefined);
  readonly placeholder = input('Cerca una categoria');

  private readonly combobox = viewChild(Combobox);
  private readonly listbox = viewChild(Listbox);

  protected readonly expanded = signal(false);

  private readonly selected = computed(() =>
    this.categories().find((category) => category.id === this.value()),
  );

  /** Ciò che il campo mostra a riposo: il nome della scelta. */
  private readonly display = computed(() =>
    this.value() === '' ? (this.emptyLabel() ?? '') : (this.selected()?.name ?? ''),
  );

  protected readonly query = linkedSignal(() => this.display());

  protected readonly selection = linkedSignal(() => [this.value()]);

  /** Il campo che mostra ancora la scelta non è una ricerca: si vede tutto. */
  protected readonly search = computed(() => {
    const query = this.query();
    return query === this.display() ? '' : normalized(query.trim());
  });

  protected readonly groups = computed<readonly PickerGroup[]>(() => {
    const search = this.search();
    const exclude = this.exclude();
    return categoryTree(this.categories())
      .map(({ macro, children }) => {
        const available = children.filter((child) => child.id !== exclude);
        const visible =
          search === '' || normalized(macro.name).includes(search)
            ? available
            : available.filter((child) => normalized(child.name).includes(search));
        return { id: macro.id, name: macro.name, children: visible };
      })
      .filter((group) => group.children.length > 0);
  });

  constructor() {
    // Chiuso senza scegliere, il campo torna a dire la scelta di prima.
    effect(() => {
      if (!this.expanded()) this.query.set(this.display());
    });

    afterRenderEffect(() => {
      if (this.expanded()) this.listbox()?.scrollActiveItemIntoView();
    });
  }

  protected commit(): void {
    const [id] = this.selection();
    if (id !== undefined) this.value.set(id);
    this.expanded.set(false);
    this.combobox()?.element.focus();
  }
}
