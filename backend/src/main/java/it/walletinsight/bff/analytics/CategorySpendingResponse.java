package it.walletinsight.bff.analytics;

/**
 * Quanto è uscito in una categoria, nel periodo chiesto.
 *
 * Porta il **nome** e non solo l'identificatore, ed è il lavoro che giustifica
 * il BFF: gli importi li conta `core.movements`, che le categorie non sa
 * nemmeno come si chiamano, e i nomi li ha `core.categories`. I due moduli non
 * si conoscono; comporli qui costa una lettura in più dell'anagrafica e
 * risparmia al browser una seconda richiesta e una join fatta a mano.
 *
 * `share` è la quota sul totale delle uscite del periodo, fra 0 e 1. La calcola
 * il server perché la calcola su **tutte** le categorie, non solo su quelle che
 * entrano nella classifica: farlo nel client sulle prime cinque darebbe
 * percentuali che sommano a 100 e mentono.
 *
 * @param categoryId identificatore di Wallet Insights della categoria
 * @param name       il nome mostrato: dell'utente se l'ha cambiato
 * @param color      colore della categoria (`#rrggbb`), assente se nessuno ne ha uno
 * @param totalCents quanto è uscito, positivo, in centesimi interi
 * @param count      quanti movimenti
 * @param share      quota sul totale delle uscite del periodo, fra 0 e 1
 */
public record CategorySpendingResponse(
        String categoryId,
        String name,
        String color,
        long totalCents,
        long count,
        double share) {
}
