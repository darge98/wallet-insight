package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.util.List;

/**
 * Una pagina di categorie, con la stessa paginazione dei conti e dei movimenti.
 *
 * Le categorie arrivano anche denormalizzate dentro ogni movimento, ma leggerle
 * da qui è un'altra cosa: un import incrementale guarda un giorno solo e nei
 * movimenti di quel giorno vedrebbe una manciata di categorie su settanta.
 * Questo endpoint le dà tutte, quindi anche una rinomina fatta su una categoria
 * che oggi non ha movimenti arriva lo stesso.
 */
public record CategoriesPageDto(
        List<CategoryDto> categories,
        Integer limit,
        Integer offset,
        Integer nextOffset,
        Integer total) {

    public boolean hasMore() {
        return nextOffset != null;
    }
}
