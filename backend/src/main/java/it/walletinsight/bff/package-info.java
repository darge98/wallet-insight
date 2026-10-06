/**
 * Il layer che parla col frontend: l'unico punto in cui questa applicazione espone HTTP.
 *
 * I moduli sotto `core/` non hanno endpoint propri. Sono domini — utenti, importazione — e
 * si fermano al proprio caso d'uso; qui vivono i controller e i DTO, e qui i casi d'uso si
 * compongono. Una schermata che ha bisogno di due domini fa **una** chiamata sola
 * (`/api/onboarding`: profilo e sorgente insieme), invece di orchestrare dal browser una
 * sequenza che può rompersi a metà.
 *
 * È anche il confine del contratto: kebab-case, `Page<T>`, importi in centesimi e
 * ProblemDetail si decidono in un posto solo, e cambiare la forma di una risposta non
 * obbliga a entrare in un modulo di dominio.
 *
 * Dipende dai moduli attraverso due sole aperture — `domain` (i tipi) e `application` (i
 * servizi) — mai da `infrastructure`: che una credenziale sia cifrata o che un elenco
 * arrivi da PostgreSQL resta un fatto del modulo che la possiede.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "BFF",
        allowedDependencies = {
                "core.users::domain", "core.users::application",
                "core.ingestion::domain", "core.ingestion::application",
                "core.accounts::domain", "core.accounts::application",
                "core.categories::domain", "core.categories::application",
                "core.movements::domain", "core.movements::application",
                "core.budgets::domain", "core.budgets::application",
                "core.subscriptions::domain", "core.subscriptions::application"
        })
package it.walletinsight.bff;
