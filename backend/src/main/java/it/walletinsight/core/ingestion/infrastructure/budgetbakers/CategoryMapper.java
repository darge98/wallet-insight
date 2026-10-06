package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import it.walletinsight.core.categories.domain.ImportedCategory;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoryDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoryGroupDto;
import org.springframework.stereotype.Component;



/**
 * Traduce una categoria di BudgetBakers in una {@link ImportedCategory}, con la
 * voce dell'elenco di base in cui confluisce. Una categoria creata dall'utente in
 * BudgetBakers segue quella standard da cui deriva.
 */
@Component
class CategoryMapper {

    ImportedCategory toImported(CategoryDto dto) {
        String gruppo = groupOf(dto);
        String origine = dto.parentId() == null || dto.parentId().isBlank() ? null : dto.parentId();
        return new ImportedCategory(
                dto.id(),
                dto.name(),
                gruppo,
                origine,
                BudgetBakersCategories.templateFor(origine != null ? origine : dto.id(), gruppo));
    }

    /** L'identificativo del gruppo e non il nome: il nome è testo tradotto. */
    private static String groupOf(CategoryDto dto) {
        CategoryGroupDto group = dto.group();
        if (group == null) {
            return null;
        }
        return group.id() != null ? group.id() : group.name();
    }
}
