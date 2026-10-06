package it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un movimento come arriva da {@code GET /v1/api/records}.
 *
 * Nessun campo è dichiarato obbligatorio dalla specifica, quindi qui sono tutti
 * tipi che ammettono null. `note` e `counterParty` spariscono proprio come
 * chiave quando sono vuoti, mentre `transfer` resta presente valorizzato a
 * null: due comportamenti diversi nello stesso oggetto, entrambi gestiti allo
 * stesso modo da Jackson.
 *
 * `convertedAmount` arriva solo se la richiesta porta `convertTo`. I campi
 * `photos` e `place` esistono nella specifica ma non compaiono in nessuno dei
 * movimenti osservati: non sono modellati, e la deserializzazione è configurata
 * per ignorare ciò che non conosce.
 */
public record RecordDto(
        String id,
        String accountId,
        String accountName,
        Boolean accountIsBankSync,
        String note,
        String counterParty,
        AmountDto amount,
        ConvertedAmountDto convertedAmount,
        Instant recordDate,
        CategoryDto category,
        String recordState,
        String recordType,
        List<LabelDto> labels,
        TransferDto transfer,
        String source,
        Instant createdAt,
        Instant updatedAt) {
}
