import { Injectable, computed, inject, resource, signal } from '@angular/core';

import {
  Category,
  CategoryChanges,
  CategoryId,
  CategoryRejectedError,
  CATEGORY_REPOSITORY,
  categoryTree,
  SourceCategory,
  SourceCategoryId,
} from '@wallet/shared-domain';

/** Ciò che il pannello sta modificando: `category` null vuol dire «nuova» dentro `parent`. */
export interface CategoryEditing {
  readonly category: Category | null;
  readonly parent: Category | null;
}

/**
 * Le categorie dell'utente e i loro agganci con le sorgenti.
 *
 * Dopo ogni scrittura si rileggono entrambi gli elenchi: unire una categoria
 * sposta anche gli agganci, e la risposta del server è la sola verità.
 */
@Injectable()
export class CategoriesFacade {
  private readonly repository = inject(CATEGORY_REPOSITORY);

  readonly categories = resource({
    loader: ({ abortSignal }) => this.repository.findAll(abortSignal),
    defaultValue: [] as readonly Category[],
  });

  readonly sources = resource({
    loader: ({ abortSignal }) => this.repository.findSources(abortSignal),
    defaultValue: [] as readonly SourceCategory[],
  });

  readonly tree = computed(() => categoryTree(this.categories.value()));

  readonly categoryById = computed(
    () => new Map(this.categories.value().map((category) => [category.id, category])),
  );

  private readonly failureState = signal<string | null>(null);
  private readonly busyState = signal(false);
  private readonly editingState = signal<CategoryEditing | null>(null);

  readonly failure = this.failureState.asReadonly();
  readonly busy = this.busyState.asReadonly();
  readonly editing = this.editingState.asReadonly();

  /** Una macro nuova se `parent` è `null`, altrimenti una sua sottocategoria. */
  openNew(parent: Category | null): void {
    this.failureState.set(null);
    this.editingState.set({ category: null, parent });
  }

  open(category: Category): void {
    const parent = category.parentId ? (this.categoryById().get(category.parentId) ?? null) : null;
    this.failureState.set(null);
    this.editingState.set({ category, parent });
  }

  close(): void {
    this.editingState.set(null);
  }

  /** Crea o modifica ciò che è aperto nel pannello. */
  async save(name: string, parentId: CategoryId | null): Promise<void> {
    const editing = this.editingState();
    if (!editing) return;
    const category = editing.category;
    await this.write(() => {
      if (category === null) {
        return this.repository.create(name, editing.parent?.id ?? null);
      }
      const changes: CategoryChanges = {
        ...(name !== category.name ? { name } : {}),
        ...(parentId && parentId !== category.parentId ? { parentId } : {}),
      };
      return this.repository.update(category.id, changes);
    });
  }

  /** Toglie la categoria aperta; una sottocategoria passa i movimenti a `into`. */
  async remove(into: CategoryId | null): Promise<void> {
    const category = this.editingState()?.category;
    if (!category) return;
    await this.write(() => this.repository.remove(category.id, into));
  }

  async relink(id: SourceCategoryId, categoryId: CategoryId): Promise<void> {
    await this.write(() => this.repository.relink(id, categoryId), false);
  }

  dismissFailure(): void {
    this.failureState.set(null);
  }

  /** Dopo una scrittura riuscita il pannello si chiude: ciò che resta è la risposta del server. */
  private async write(operation: () => Promise<unknown>, closes = true): Promise<void> {
    this.busyState.set(true);
    this.failureState.set(null);
    try {
      await operation();
      if (closes) this.editingState.set(null);
    } catch (failure) {
      this.failureState.set(
        failure instanceof CategoryRejectedError
          ? failure.message
          : 'Non è stato possibile salvare. Riprova tra poco.',
      );
    } finally {
      this.busyState.set(false);
      this.categories.reload();
      this.sources.reload();
    }
  }
}
