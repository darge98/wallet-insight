package it.walletinsight.core.ingestion.domain;

import it.walletinsight.shared.source.IngestionSource;

import java.time.LocalDate;

/**
 * Com'è andato l'import di una sorgente.
 *
 * Esiste perché un import ora si può chiedere dall'interfaccia: l'utente preme
 * "aggiorna" e si aspetta che qualcosa gli risponda *cos'è successo*. Un log non
 * basta più — nessuno lo legge dal browser.
 *
 * Un fallimento è un esito, non un'eccezione che sfugge: le sorgenti di un utente
 * sono indipendenti, e un token scaduto su una non è una buona ragione per non
 * dire com'è andata sulle altre.
 *
 * Letti e salvati sono due numeri diversi di proposito. Ogni giro rilegge di
 * proposito il giorno già importato, quindi "letti 40, salvati 0" è l'esito
 * normale di un aggiornamento senza novità — e distinguerlo da "non ha
 * funzionato" è tutta la differenza per chi ha premuto il pulsante.
 *
 * @param source            la sorgente importata
 * @param accountsSynced    quanti conti sono stati allineati
 * @param categoriesSynced  quante categorie sono state allineate
 * @param movementsRead   quanti movimenti sono stati letti dalla sorgente in questo giro
 * @param movementsSaved  quanti ne sono stati creati o riallineati davvero
 * @param lastRecordDate  il segnaposto dopo l'import, null se non si è mai letto nulla
 * @param failure         il motivo del fallimento, null se è andata bene
 */
public record ImportOutcome(
        IngestionSource source,
        int accountsSynced,
        int categoriesSynced,
        int movementsRead,
        int movementsSaved,
        LocalDate lastRecordDate,
        String failure) {

    public static ImportOutcome succeeded(IngestionSource source, int accountsSynced,
                                          int categoriesSynced, int movementsRead,
                                          int movementsSaved, LocalDate lastRecordDate) {
        return new ImportOutcome(source, accountsSynced, categoriesSynced,
                movementsRead, movementsSaved, lastRecordDate, null);
    }

    public static ImportOutcome failed(IngestionSource source, String failure) {
        return new ImportOutcome(source, 0, 0, 0, 0, null, failure);
    }

    public boolean succeeded() {
        return failure == null;
    }
}
