package it.walletinsight.core.ingestion.domain;

/**
 * La sorgente ha rifiutato le credenziali della connessione: token scaduto o revocato.
 *
 * Gli adapter di {@link ImportSource} la sollevano al posto di un errore generico
 * perché è l'unico fallimento che può risolvere l'utente, incollando un token nuovo.
 */
public class RejectedCredentialsException extends RuntimeException {

    public RejectedCredentialsException(String message) {
        super(message);
    }
}
