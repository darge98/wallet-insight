package it.walletinsight.bff.imports;

import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.platform.web.KebabCase;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Forma JSON della connessione, identica a `ImportConnection` del frontend.
 *
 * Il segreto non c'è: una volta salvato non torna più indietro, e di lui resta
 * solo la traccia (`…a1b2`) perché l'utente riconosca quale token ha collegato.
 * Con `default-property-inclusion: non_null` un hint assente viene omesso e
 * l'adapter del frontend lo riporta a `null`.
 *
 * Le due date dicono cose diverse e servono entrambe: `lastRecordDate` è fino a
 * quando si hanno i dati, `lastRunAt` è quando si è guardato l'ultima volta. Un
 * aggiornamento che non trova nulla muove solo la seconda — ed è la risposta a
 * "sono aggiornato?", che la prima da sola non dà.
 *
 * @param source         identificativo della sorgente in kebab-case
 * @param enabled        se l'aggiornamento automatico la considera
 * @param secretHint     le ultime cifre del token, per riconoscerlo; mai il valore
 * @param configuredAt   quando è stata collegata
 * @param lastRecordDate data del dato più recente importato, assente se mai letta
 * @param lastRunAt      istante dell'ultimo import riuscito, assente se mai arrivato in fondo
 * @param credentialsRejectedAt quando la sorgente ha rifiutato il token, assente se non va sostituito
 */
public record ImportConnectionResponse(
        String source,
        boolean enabled,
        String secretHint,
        LocalDate configuredAt,
        LocalDate lastRecordDate,
        Instant lastRunAt,
        Instant credentialsRejectedAt) {

    public static ImportConnectionResponse from(ImportConnection connection) {
        return new ImportConnectionResponse(
                KebabCase.from(connection.source()),
                connection.enabled(),
                connection.token().hint(),
                connection.configuredAt(),
                connection.lastRecordDate(),
                connection.lastRunAt(),
                connection.credentialsRejectedAt());
    }
}
