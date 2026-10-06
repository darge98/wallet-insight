package it.walletinsight.bff.imports;

import it.walletinsight.core.ingestion.domain.ImportOutcome;
import it.walletinsight.platform.web.KebabCase;

import java.time.LocalDate;

/**
 * Com'è andato l'import di una sorgente, per chi ha premuto "aggiorna".
 *
 * Un fallimento non è un errore HTTP: le sorgenti sono indipendenti, e se una su
 * tre non risponde l'utente deve vedere che le altre due sono andate bene. Per
 * questo la risposta è sempre 200 con un esito per sorgente, e `failure` dice
 * quale non ce l'ha fatta — un 500 nasconderebbe il lavoro riuscito.
 *
 * @param source          identificativo della sorgente in kebab-case
 * @param succeeded       se l'import è arrivato in fondo
 * @param accountsSynced  quanti conti sono stati allineati
 * @param categoriesSynced quante categorie sono state allineate
 * @param movementsRead   quanti movimenti sono stati letti dalla sorgente in questo giro
 * @param movementsSaved  quanti ne sono stati creati o riallineati davvero
 * @param lastRecordDate  il segnaposto dopo l'import, assente se non si è mai letto nulla
 * @param failure         il motivo del fallimento, assente se è andata bene
 */
public record ImportOutcomeResponse(
        String source,
        boolean succeeded,
        int accountsSynced,
        int categoriesSynced,
        int movementsRead,
        int movementsSaved,
        LocalDate lastRecordDate,
        String failure) {

    public static ImportOutcomeResponse from(ImportOutcome outcome) {
        return new ImportOutcomeResponse(
                KebabCase.from(outcome.source()),
                outcome.succeeded(),
                outcome.accountsSynced(),
                outcome.categoriesSynced(),
                outcome.movementsRead(),
                outcome.movementsSaved(),
                outcome.lastRecordDate(),
                outcome.failure());
    }
}
