package it.walletinsight.core.movements.domain;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.shared.daterange.DateRange;

import java.util.List;
import java.util.Set;

/**
 * I criteri con cui si chiede un elenco di movimenti: tutti facoltativi.
 *
 * Un periodo {@code null} e una collezione vuota significano entrambi «non
 * filtrare», e la combinazione di tutti è «tutto lo storico». Le collezioni sono
 * vuote e mai {@code null} perché «nessun filtro» e «filtro senza valori» sono la
 * stessa cosa, e lasciarle nullable moltiplicherebbe i casi da gestire in ogni
 * punto che le legge.
 *
 * Criteri diversi si combinano in **and** — periodo *e* conto *e* categoria —
 * mentre più valori dello stesso criterio si combinano in **or**: chiedere due
 * conti vuol dire «uno qualsiasi dei due», non «entrambi», che su un movimento
 * solo non avrebbe senso.
 *
 * Il filtro per categoria guarda la categoria **mostrata** — quella scelta
 * dall'utente se ha riclassificato, altrimenti quella della sorgente: chiedere
 * «i movimenti in Ristoranti» e non vedere quello che ci si è appena spostato a
 * mano sarebbe incomprensibile.
 *
 * Non c'è la ricerca testuale, e non è una dimenticanza: è una scelta rimandata.
 * Farla bene su descrizione e controparte vuol dire un indice apposta e decidere
 * cosa significhi «trovare» — prefissi, accenti, parole in mezzo — e aggiungerla
 * come un `like '%…%'` costerebbe una scansione a ogni tasto premuto per dare
 * risultati che sembrano casuali.
 */
public record MovementFilter(
        DateRange period,
        Set<MovementKind> kinds,
        List<AccountId> accountIds,
        List<CategoryId> categoryIds) {

    public MovementFilter {
        kinds = kinds == null ? Set.of() : Set.copyOf(kinds);
        accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
    }

    public static MovementFilter all() {
        return new MovementFilter(null, Set.of(), List.of(), List.of());
    }

    public static MovementFilter inPeriod(DateRange period) {
        return new MovementFilter(period, Set.of(), List.of(), List.of());
    }
}
