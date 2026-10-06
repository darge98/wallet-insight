/**
 * Le categorie: come i movimenti sono classificati.
 *
 * Sono di Wallet Insights e dell'utente, non della sorgente: un albero a due livelli
 * (macro → sottocategoria) che parte dall'elenco di base ({@code DefaultCategories})
 * e che l'utente rinomina, sposta, crea e unisce. Le categorie della sorgente
 * restano a parte ({@code SourceCategory}), ognuna agganciata a una delle sue:
 * l'import classifica attraverso l'aggancio, e riagganciare è il modo di
 * riclassificare tutto ciò che arriva da quella categoria.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Categories",
        allowedDependencies = "core.users::domain")
package it.walletinsight.core.categories;
