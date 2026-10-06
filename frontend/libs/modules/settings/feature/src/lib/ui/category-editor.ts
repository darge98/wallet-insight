import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';

import { Category, CategoryBranch, CategoryId } from '@wallet/shared-domain';
import { Alert, Icon } from '@wallet/shared-ui';
import { CategoryPicker } from '@wallet/shared-ui/category-picker';

/**
 * Il pannello di una categoria: nome e macro, e in fondo come toglierla.
 *
 * Una sottocategoria si toglie solo unendola a un'altra, che ne prende movimenti
 * e agganci; una macro solo vuota. Sono le regole del server, dette prima.
 */
@Component({
  selector: 'app-category-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alert, CategoryPicker, Icon],
  host: { class: 'contents' },
  templateUrl: './category-editor.html',
})
export class CategoryEditor {
  readonly open = input(false);
  /** `null` vuol dire «nuova». */
  readonly category = input<Category | null>(null);
  /** La macro: quella in cui crearla, o quella in cui sta. `null` per una macro. */
  readonly parent = input<Category | null>(null);
  readonly tree = input<readonly CategoryBranch[]>([]);
  readonly busy = input(false);
  readonly failure = input<string | null>(null);

  readonly closed = output<void>();
  readonly saved = output<{ name: string; parentId: CategoryId | null }>();
  readonly removed = output<CategoryId | null>();

  protected readonly name = signal('');
  protected readonly parentId = signal<string>('');
  protected readonly mergeInto = signal<string>('');

  protected readonly categories = computed(() =>
    this.tree().flatMap((branch) => [branch.macro, ...branch.children]),
  );
  protected readonly confirming = signal(false);

  constructor() {
    effect(() => {
      this.open();
      this.name.set(this.category()?.name ?? '');
      this.parentId.set(this.parent()?.id ?? '');
      this.mergeInto.set('');
      this.confirming.set(false);
    });
  }

  protected readonly isMacro = computed(() => this.parent() === null);

  protected readonly title = computed(() => {
    const category = this.category();
    if (category) return category.name;
    const parent = this.parent();
    return parent ? `Nuova sottocategoria di «${parent.name}»` : 'Nuova macro';
  });

  protected readonly children = computed(() => {
    const category = this.category();
    return category
      ? (this.tree().find((branch) => branch.macro.id === category.id)?.children ?? [])
      : [];
  });

  protected readonly canSave = computed(() => this.name().trim() !== '' && !this.busy());

  protected save(): void {
    if (!this.canSave()) return;
    this.saved.emit({
      name: this.name().trim(),
      parentId: this.parentId() ? (this.parentId() as CategoryId) : null,
    });
  }

  protected remove(): void {
    const into = this.mergeInto();
    this.removed.emit(into ? (into as CategoryId) : null);
  }
}
