package it.walletinsight.platform.web;

/**
 * Sollevata quando una riga letta dal database viola il contratto dell'applicazione: un
 * valore di enum o di valuta che nessun percorso di scrittura dovrebbe poter produrre.
 *
 * A differenza di {@link IllegalArgumentException}, che {@link ApiExceptionHandler}
 * considera sicura da esporre al client, il messaggio di questa eccezione contiene il
 * valore letto dal database e non è pubblico: è un errore di dati, non una richiesta
 * malformata. {@code ApiExceptionHandler} lo logga ma non lo mette nel corpo della
 * risposta, che riceve un 500 generico.
 */
public class CorruptedDataException extends RuntimeException {

    public CorruptedDataException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Messaggio uniforme per un valore di colonna che un row mapper non riesce a tradurre. */
    public static CorruptedDataException invalidColumn(
            String table, String column, String rawValue, Throwable cause) {
        return new CorruptedDataException(
                "Valore non ammesso in %s.%s: '%s'.".formatted(table, column, rawValue), cause);
    }
}
