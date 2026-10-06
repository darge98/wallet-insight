package it.walletinsight.core.categories.domain;

import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.source.IngestionSource;

import java.util.Objects;

/**
 * Una categoria come la dichiara una sorgente, agganciata a una categoria di Wallet Insights.
 *
 * Nome, gruppo e colore sono della sorgente e ogni import li riscrive. L'aggancio
 * ({@code category}) è dell'utente: lo fissa il primo import dall'elenco di base,
 * poi nessun import lo tocca. {@code null} vuol dire «non agganciata»: i suoi
 * movimenti entrano senza categoria.
 */
public record SourceCategory(
        SourceCategoryId id,
        UserId userId,
        IngestionSource source,
        String externalId,
        String name,
        String group,
        String parentExternalId,
        CategoryId category) {

    public SourceCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(name, "name");
    }

    /** Riallineata a ciò che la sorgente dice oggi, con l'aggancio dell'utente intatto. */
    public SourceCategory refreshedFrom(ImportedCategory fromSource) {
        return new SourceCategory(id, userId, source, externalId, fromSource.name(),
                fromSource.group(), fromSource.parentExternalId(), category);
    }

    public SourceCategory linkedTo(CategoryId newCategory) {
        return new SourceCategory(id, userId, source, externalId, name, group, parentExternalId,
                newCategory);
    }
}
