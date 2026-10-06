package it.walletinsight.bff.accounts;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.core.accounts.domain.Account;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Ciò che di un conto l'utente può cambiare: come si chiama e di che colore lo vede.
 *
 * È un PATCH, quindi un campo **assente vale "non toccare"**, non "azzera": si può
 * rinominare senza dire nulla sul colore e viceversa. Per questo `name` non è
 * `@NotBlank` come lo sarebbe in una sostituzione completa — ma una stringa vuota
 * inviata *esplicitamente* resta un errore, e la regola sotto è quella che lo dice.
 *
 * Il resto del conto non compare, e non è una svista: tipo, valuta, saldo iniziale
 * e archiviazione sono fatti della sorgente, e ogni import li riscrive. Metterli
 * qui prometterebbe una modifica che il prossimo aggiornamento cancellerebbe.
 */
public record UpdateAccountRequest(
        // `@Pattern` e non `@NotBlank`: quest'ultimo rifiuterebbe anche l'assenza, che
        // qui è legittima. Su un valore nullo `@Pattern` non si pronuncia, su uno di
        // soli spazi sì — che è esattamente la distinzione che serve a un PATCH.
        @Size(max = Account.MAX_NAME_LENGTH)
        @jakarta.validation.constraints.Pattern(regexp = ".*\\S.*", message = "non può essere vuoto")
        @Schema(example = "Conto stipendio") String name,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "deve essere nel formato #rrggbb")
        @Schema(example = "#f97316") String color) {
}
