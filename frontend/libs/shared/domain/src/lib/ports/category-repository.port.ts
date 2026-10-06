import { InjectionToken } from '@angular/core';

import { Category, SourceCategory } from '../categories/category';
import { CategoryId, SourceCategoryId } from '../shared/identifier';

/** Le modifiche di una categoria: un campo assente resta com'è, un colore vuoto lo toglie. */
export interface CategoryChanges {
  readonly name?: string;
  readonly color?: string;
  /** Sposta una sottocategoria in un'altra macro. */
  readonly parentId?: CategoryId;
}

/**
 * Le categorie di Wallet Insights e i loro agganci con le sorgenti. Le scritture
 * restituiscono ciò che il server ha salvato; un rifiuto delle regole arriva come
 * {@link CategoryRejectedError}.
 */
export interface CategoryRepository {
  findAll(signal?: AbortSignal): Promise<readonly Category[]>;
  /** Una macro se `parentId` è `null`. */
  create(name: string, parentId: CategoryId | null, signal?: AbortSignal): Promise<Category>;
  update(id: CategoryId, changes: CategoryChanges, signal?: AbortSignal): Promise<Category>;
  /** Una sottocategoria si unisce a `into`, che ne prende i movimenti; una macro si toglie vuota. */
  remove(id: CategoryId, into: CategoryId | null, signal?: AbortSignal): Promise<void>;
  findSources(signal?: AbortSignal): Promise<readonly SourceCategory[]>;
  /** I movimenti già importati da quella categoria la seguono. */
  relink(
    id: SourceCategoryId,
    categoryId: CategoryId,
    signal?: AbortSignal,
  ): Promise<SourceCategory>;
}

/** Una modifica che le regole non ammettono: il messaggio è per l'utente. */
export class CategoryRejectedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'CategoryRejectedError';
  }
}

export const CATEGORY_REPOSITORY = new InjectionToken<CategoryRepository>('CategoryRepository');
