package it.walletinsight.core.movements.domain;

import it.walletinsight.core.categories.domain.CategoryId;

/**
 * Come un movimento è classificato: la categoria, più la traccia grezza con cui
 * la sorgente la chiama.
 *
 * La categoria è una, non due. Gliela assegna l'import quando il movimento entra,
 * da lì in poi è del movimento e nessun import la sposta: non c'è un ripiego da
 * calcolare, e {@code null} vuol dire che una categoria non c'è.
 *
 * {@code sourceExternalId} e {@code sourceName} non sono un doppione di ciò che
 * la riga in {@code categories} già contiene, e non servono a mostrare niente.
 * Sono ciò che permette di riagganciare un movimento alla sua categoria quando
 * il riferimento risolto manca — una categoria creata nella sorgente dopo i
 * movimenti che ci stanno dentro — senza chiedere niente alla sorgente.
 *
 * Può non esserci nessuna categoria, anche se nei 1678 movimenti misurati non
 * succede mai: un movimento senza classificazione resta un movimento, e
 * rifiutarlo perderebbe il suo importo.
 */
public record Classification(CategoryId category, String sourceExternalId, String sourceName) {

    public Classification {
        sourceExternalId = blankToNull(sourceExternalId);
        sourceName = blankToNull(sourceName);
    }

    /** Un movimento che la sorgente non classifica. */
    public static Classification none() {
        return new Classification(null, null, null);
    }

    /** Come arriva da un import: la categoria risolta e la sua traccia. */
    public static Classification fromSource(
            CategoryId category, String sourceExternalId, String sourceName) {
        return new Classification(category, sourceExternalId, sourceName);
    }

    /** Copia spostata su un'altra categoria. */
    public Classification movedTo(CategoryId newCategory) {
        return new Classification(newCategory, sourceExternalId, sourceName);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
