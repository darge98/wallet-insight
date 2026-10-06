package it.walletinsight.core.ingestion.domain;

/**
 * La sorgente non ha risposto, o ha risposto con un suo errore: rete giù, timeout, 5xx.
 *
 * Come {@link RejectedCredentialsException} porta un messaggio scritto per l'utente,
 * che quindi può arrivare fino all'interfaccia; il dettaglio tecnico sta nella causa e
 * finisce solo nei log. Non è colpa di nessuno dei due: il giro dopo riprova.
 */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
