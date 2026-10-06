import { CategoryId, SourceCategoryId } from '../shared/identifier';

/**
 * Una categoria di Wallet Insights: una macro se `parentId` è `null`, altrimenti una
 * sottocategoria. I movimenti stanno solo nelle sottocategorie.
 *
 * Sono dell'utente: partono da un elenco di base e si rinominano, spostano,
 * creano e uniscono. Le categorie delle sorgenti sono un'altra cosa
 * ({@link SourceCategory}), agganciate a queste.
 */
export interface Category {
  readonly id: CategoryId;
  readonly parentId: CategoryId | null;
  readonly name: string;
  /** `#rrggbb`; `null` se l'utente non ne ha scelto uno. */
  readonly color: string | null;
}

/** Una categoria di una sorgente e la categoria di Wallet Insights in cui confluisce. */
export interface SourceCategory {
  readonly id: SourceCategoryId;
  readonly source: string;
  readonly name: string;
  /** Il gruppo della sorgente, grezzo (`food_and_drinks`). */
  readonly group: string | null;
  /** `null` se non agganciata: i suoi movimenti entrano senza categoria. */
  readonly categoryId: CategoryId | null;
}

/** Una macro con le sue sottocategorie. */
export interface CategoryBranch {
  readonly macro: Category;
  readonly children: readonly Category[];
}

export const isMacroCategory = (category: Category): boolean => category.parentId === null;

const byName = (a: Category, b: Category): number => a.name.localeCompare(b.name, 'it');

/** L'albero delle categorie, macro e figlie in ordine alfabetico. */
export function categoryTree(categories: readonly Category[]): readonly CategoryBranch[] {
  return categories
    .filter(isMacroCategory)
    .sort(byName)
    .map((macro) => ({
      macro,
      children: categories.filter((category) => category.parentId === macro.id).sort(byName),
    }));
}

export function indexCategoriesById(
  categories: readonly Category[],
): ReadonlyMap<CategoryId, Category> {
  return new Map(categories.map((category) => [category.id, category]));
}
