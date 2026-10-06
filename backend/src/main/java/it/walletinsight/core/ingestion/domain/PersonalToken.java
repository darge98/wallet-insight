package it.walletinsight.core.ingestion.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Il segreto con cui una sorgente autentica le proprie chiamate.
 *
 * È il valore in chiaro: esiste in memoria il tempo di arrivare al repository,
 * che lo cifra prima di scriverlo. Per questo {@link #toString()} è sovrascritto
 * e restituisce solo la traccia: un record Java stamperebbe altrimenti il segreto
 * in ogni log, messaggio d'errore o dump di debug che incontra l'aggregato.
 *
 * La forma verificata è quella del JWT compatto, l'unico formato di token
 * personale in uso (BudgetBakers): intercetta subito l'errore più frequente,
 * cioè l'incollatura parziale. Quando arriverà una sorgente con un segreto di
 * forma diversa (una API key, una password) la regola andrà spostata accanto
 * alla sorgente; il resto — cifratura compresa — non ne sa nulla.
 */
public record PersonalToken(String value) {

    public static final int MAX_LENGTH = 4096;

    /** Tre segmenti base64url separati da un punto. La firma non la verifichiamo: non abbiamo la chiave. */
    private static final Pattern COMPACT_JWT =
            Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    /** Sotto questa lunghezza la traccia rivelerebbe una parte significativa del segreto. */
    private static final int MIN_LENGTH_FOR_HINT = 8;

    private static final int HINT_DIGITS = 4;

    public PersonalToken {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Il token è obbligatorio.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Il token supera i %d caratteri.".formatted(MAX_LENGTH));
        }
        if (!COMPACT_JWT.matcher(value).matches()) {
            // Messaggio pubblico (finisce nel ProblemDetail): descrive la forma attesa, mai il valore.
            throw new IllegalArgumentException("Il token personale non è un JWT valido.");
        }
    }

    /**
     * Traccia riconoscibile del segreto, da mostrare al posto del segreto stesso:
     * serve solo perché l'utente riconosca *quale* token ha salvato.
     */
    public String hint() {
        return value.length() < MIN_LENGTH_FOR_HINT
                ? null
                : "…" + value.substring(value.length() - HINT_DIGITS);
    }

    @Override
    public String toString() {
        return "PersonalToken[" + hint() + "]";
    }
}
