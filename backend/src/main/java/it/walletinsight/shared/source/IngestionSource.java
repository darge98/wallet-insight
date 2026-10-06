package it.walletinsight.shared.source;

/**
 * Le sorgenti da cui l'applicazione può importare i movimenti.
 *
 * L'elenco e i suoi attributi rispecchiano `IMPORT_SOURCE_CATALOG` del frontend
 * (`libs/ingestion/domain`): i due lati devono restare allineati, perché è questo
 * identificativo — kebab-case sul filo, tradotto da `KebabCase` — che viaggia
 * nelle richieste.
 *
 * Il catalogo vive qui e non in una tabella perché non è un dato dell'utente:
 * dice quali integrazioni *esistono in questa versione del codice*, e cambia con
 * un rilascio, non con un insert.
 */
public enum IngestionSource {

    BUDGET_BAKERS(ImportCredentialKind.PERSONAL_TOKEN, true),

    /** Visibile ma non ancora collegabile: l'integrazione non esiste. */
    PSD2(ImportCredentialKind.OAUTH, false);

    private final ImportCredentialKind credential;
    private final boolean available;

    IngestionSource(ImportCredentialKind credential, boolean available) {
        this.credential = credential;
        this.available = available;
    }

    public ImportCredentialKind credential() {
        return credential;
    }

    public boolean available() {
        return available;
    }
}
