/**
 * I budget: limiti mensili di spesa su gruppi di categorie, su due livelli.
 *
 * A differenza di conti e categorie non nascono da un import: li scrive l'utente,
 * quindi non portano `source` né `external_id`. La spesa non la conoscono — la
 * sommano i movimenti, e a metterla accanto ai limiti è il BFF.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Budgets",
        allowedDependencies = {"core.users::domain", "core.categories::domain"})
package it.walletinsight.core.budgets;
