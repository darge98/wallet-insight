package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.util.List;

/**
 * Una pagina di movimenti.
 *
 * `nextOffset` è presente solo finché esistono altre pagine: è la condizione di
 * uscita del ciclo di paginazione. `total` arriva solo chiedendo
 * {@code withTotal=true}.
 *
 * `appliedRecordDateFilters` dichiara il filtro data realmente applicato dal
 * server, compreso quello che applica di sua iniziativa quando non ne
 * specifichiamo uno: senza `recordDate` esplicito restituisce soltanto gli
 * ultimi tre mesi.
 */
public record RecordsPageDto(
        List<RecordDto> records,
        Integer limit,
        Integer offset,
        Integer nextOffset,
        Integer total,
        List<String> appliedRecordDateFilters) {

    public boolean hasMore() {
        return nextOffset != null;
    }
}
