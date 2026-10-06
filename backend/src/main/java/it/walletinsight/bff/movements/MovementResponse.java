package it.walletinsight.bff.movements;

import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.platform.web.KebabCase;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Un movimento come lo vede il frontend.
 *
 * Descrizione, pagatore/pagante e categoria sono **un campo ciascuno**, e non più
 * una coppia con accanto il valore della sorgente e un booleano a dire chi l'ha
 * scritto. Da V11 quella distinzione non esiste nemmeno nel database: l'import
 * scrive nel campo quando il movimento entra, poi il campo è il dato. Mandarne
 * una seconda versione vorrebbe dire far sapere all'interfaccia una cosa che non
 * le serve — e che, nella schermata dei movimenti, si è deciso di non mostrare.
 *
 * @param id            identificatore di Wallet Insights (UUIDv7), non quello della sorgente
 * @param accountId     il conto su cui il movimento poggia
 * @param source        la sorgente da cui è stato importato, in kebab-case
 * @param amountCents   importo con segno in centesimi interi: negativo in uscita
 * @param currencyCode  valuta ISO 4217
 * @param date          il giorno del movimento, senza ora
 * @param direction     verso dichiarato dalla sorgente, in kebab-case
 * @param state         stato di riconciliazione, in kebab-case
 * @param description   la descrizione, assente se non ce n'è una
 * @param counterParty  il pagatore o pagante, assente se non c'è
 * @param categoryId    la categoria, assente se il movimento non ne ha
 * @param transferState gamba di giroconto e suo stato di accoppiamento, assente se non lo è
 */
public record MovementResponse(
        String id,
        String accountId,
        String source,
        long amountCents,
        String currencyCode,
        LocalDate date,
        String direction,
        String state,
        String description,
        String counterParty,
        String categoryId,
        String transferState) {

    /** @param zone il fuso del profilo: decide in quale giorno cade il movimento */
    public static MovementResponse from(Movement movement, ZoneId zone) {
        CategoryId categoria = movement.classification().category();
        return new MovementResponse(
                movement.id().toString(),
                movement.accountId().toString(),
                KebabCase.from(movement.source()),
                movement.amount().amount(),
                movement.amount().currency().name(),
                movement.dateIn(zone),
                KebabCase.from(movement.direction()),
                KebabCase.from(movement.state()),
                movement.description(),
                movement.counterParty(),
                categoria == null ? null : categoria.toString(),
                movement.transfer() == null
                        ? null
                        : KebabCase.from(movement.transfer().state()));
    }
}
