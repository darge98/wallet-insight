/**
 * I movimenti: il dato per cui il resto di Wallet Insights esiste.
 *
 * Un movimento è la controparte di un movimento della sorgente e ne conserva il
 * riferimento (`source` + `externalId`), come i conti e come le categorie.
 *
 * Tre cose però sono di chi guarda, non della sorgente: la descrizione, il
 * pagatore/pagante e la categoria. Su ognuna convivono due valori — quello della
 * sorgente e quello dell'utente — e vince l'utente quando c'è; `SourcedText` e
 * `Classification` sono quella regola scritta una volta sola. La ragione è
 * misurata: la descrizione che arriva è spesso il tracciato grezzo della banca, e
 * il pagatore manca su più di un terzo dei movimenti.
 *
 * Questo **non** cambia la forma della scrittura dell'import, che resta una sola
 * istruzione in batch resa idempotente dal database: le tre colonne dell'utente
 * semplicemente non compaiono fra quelle aggiornate. Le modifiche dell'utente
 * hanno un percorso loro, che tocca solo quelle tre.
 *
 * Non c'è il saldo. Il saldo di un conto è la somma dei movimenti a partire dal
 * saldo iniziale, e la calcola PostgreSQL quando serve: una colonna con l'ultimo
 * saldo noto sarebbe una seconda verità che invecchia fra un import e l'altro.
 *
 * Dipende da `core.users::domain`, `core.accounts::domain` e
 * `core.categories::domain`: un movimento appartiene a un utente, poggia su un
 * conto che deve esistere prima, ed è classificato da una categoria che deve
 * esistere prima.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Movements",
        allowedDependencies = {
                "core.users::domain", "core.accounts::domain", "core.categories::domain"})
package it.walletinsight.core.movements;
