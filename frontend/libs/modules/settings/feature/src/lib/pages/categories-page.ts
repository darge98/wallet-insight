import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { CategoryId, SourceCategory, SourceCategoryId } from '@wallet/shared-domain';
import { Alert, EmptyState, Icon, Skeleton } from '@wallet/shared-ui';

import { CategoriesFacade } from '../data-access/categories-facade';
import { CategoryEditor } from '../ui/category-editor';

/** Le categorie di BudgetBakers, raggruppate per la macro in cui confluiscono. */
interface SourceGroup {
  readonly label: string;
  readonly sources: readonly SourceCategory[];
}

/**
 * Le categorie dell'utente e, sotto, dove confluisce ognuna di quelle di
 * BudgetBakers. Le righe sono solo nomi: ogni modifica passa dal pannello, così
 * la pagina resta leggibile anche su un telefono con sessanta sottocategorie.
 */
@Component({
  selector: 'app-categories-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [CategoriesFacade],
  imports: [Alert, CategoryEditor, EmptyState, Icon, Skeleton],
  templateUrl: './categories-page.html',
  host: { class: 'block' },
})
export class CategoriesPage {
  protected readonly facade = inject(CategoriesFacade);

  protected readonly sourceGroups = computed<readonly SourceGroup[]>(() => {
    const byTarget = new Map<string, SourceCategory[]>();
    for (const source of this.facade.sources.value()) {
      const target = source.categoryId
        ? this.facade.categoryById().get(source.categoryId)
        : undefined;
      const macro = target?.parentId ? this.facade.categoryById().get(target.parentId) : undefined;
      const label = macro?.name ?? 'Non agganciate';
      byTarget.set(label, [...(byTarget.get(label) ?? []), source]);
    }
    return [...byTarget.entries()]
      .map(([label, sources]) => ({ label, sources }))
      .sort((a, b) => a.label.localeCompare(b.label, 'it'));
  });

  protected onSaved(change: { name: string; parentId: CategoryId | null }): void {
    void this.facade.save(change.name, change.parentId);
  }

  protected onRemoved(into: CategoryId | null): void {
    void this.facade.remove(into);
  }

  protected relink(id: SourceCategoryId, categoryId: string): void {
    if (categoryId) {
      void this.facade.relink(id, categoryId as CategoryId);
    }
  }
}
