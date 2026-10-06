package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.util.List;

/**
 * Una pagina di conti, con la stessa paginazione dei movimenti.
 *
 * Paginare non è facoltativo, anche se i conti sono pochi: senza `limit` esplicito
 * l'API ne restituisce una manciata e basta — la specifica dichiara 30, la
 * risposta osservata ne ha riportati 10 — senza alcun errore. Un utente con più
 * conti del limite ne vedrebbe sparire una parte, e i loro movimenti con essa.
 */
public record AccountsPageDto(
        List<AccountDto> accounts,
        Integer limit,
        Integer offset,
        Integer nextOffset,
        Integer total) {

    public boolean hasMore() {
        return nextOffset != null;
    }
}
