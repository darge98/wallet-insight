/**
 * L'importazione: quali sorgenti l'utente ha collegato, con quali credenziali, e
 * il caso d'uso che porta i dati dentro Wallet Insights.
 *
 * Importare vive qui e non in un processo separato perché il token non deve
 * uscire da dove sta la chiave che lo decifra. Gli adapter verso le sorgenti
 * esterne (`infrastructure/budgetbakers`) sono privati al modulo: i loro DTO e i
 * loro endpoint non si vedono da nessun'altra parte, e il dominio ne conosce
 * solo la porta {@code ImportSource}.
 *
 * Dipende da `core.users::domain` (una connessione appartiene a un utente), da
 * `core.accounts` (importare significa, prima di tutto, creare i conti su cui i
 * movimenti si appoggiano), da `core.categories` (le categorie con cui quei
 * movimenti sono classificati, che devono esistere prima di loro) e da
 * `core.movements`, che i movimenti li scrive. L'ordine di quelle righe è
 * l'ordine di un import, e non è invertibile. Il catalogo delle sorgenti sta in
 * `shared/source` e non qui: lo usano anche i conti, e tenerlo dentro questo
 * modulo creerebbe un ciclo fra i due.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Ingestion",
        allowedDependencies = {
                "core.users::domain",
                "core.accounts::domain", "core.accounts::application",
                "core.categories::domain", "core.categories::application",
                "core.movements::domain", "core.movements::application"
        })
package it.walletinsight.core.ingestion;
