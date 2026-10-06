/**
 * Gli abbonamenti: spese che si rinnovano da sole a una cadenza fissa.
 *
 * Come i budget li scrive l'utente, quindi non portano `source` né `external_id`.
 * Non conoscono i movimenti: categoria e conto, facoltativi, sono dove l'addebito
 * cadrà, e preparano il passo successivo — riconoscerlo nello storico — che sarà
 * il BFF a comporre.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Subscriptions",
        allowedDependencies = {"core.users::domain", "core.categories::domain", "core.accounts::domain"})
package it.walletinsight.core.subscriptions;
