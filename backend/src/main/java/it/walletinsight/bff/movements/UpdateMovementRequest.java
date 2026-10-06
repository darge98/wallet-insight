package it.walletinsight.bff.movements;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Ciò che di un movimento l'utente può cambiare: com'è descritto, chi c'è
 * dall'altra parte, in che categoria sta.
 *
 * È un PATCH, quindi un campo **assente vale "non toccare"**; un campo presente
 * è il nuovo valore, **stringa vuota compresa**. Svuotare la descrizione la
 * lascia vuota: è ciò che l'utente ha deciso, e il testo con cui il movimento è
 * stato importato non torna a farsi vedere.
 *
 * Prima la stringa vuota voleva dire *torna a seguire la sorgente*. Quel
 * significato non c'è più: chi guarda i propri movimenti non amministra da dove
 * i dati vengono, e un campo che si rifiuta di restare vuoto è un campo rotto. Il
 * valore importato resta comunque nella sua colonna, accanto, e continua a
 * essere quello mostrato finché l'utente non scrive niente di suo.
 *
 * Il resto del movimento non compare, e non è una svista: importo, data, verso,
 * stato e giroconto sono fatti avvenuti in un'altra applicazione, e ogni import
 * li riscrive. Metterli qui prometterebbe una modifica che il prossimo
 * aggiornamento cancellerebbe.
 *
 * @param description  la descrizione dell'utente; vuota la lascia vuota
 * @param counterParty il pagatore o pagante — una persona, un negozio; vuoto lo lascia vuoto
 * @param categoryId   la categoria su cui spostare il movimento; vuota non la tocca, perché
 *                     togliere una categoria non è un'operazione che il modello conosca
 */
public record UpdateMovementRequest(
        @Schema(example = "Cena fuori") String description,

        @Schema(example = "Piadineria") String counterParty,

        // Un UUID oppure la stringa vuota, che e' quanto arriva da un movimento
        // senza categoria e non un errore di chi chiama.
        @Pattern(
                regexp = "^$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "deve essere un identificatore di categoria o la stringa vuota")
        @Schema(example = "01a0ca7b-9825-7fe8-be82-d5e4be5e9732") String categoryId) {
}
