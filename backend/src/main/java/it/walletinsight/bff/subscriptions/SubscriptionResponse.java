package it.walletinsight.bff.subscriptions;

import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.platform.web.KebabCase;

import java.time.LocalDate;

/**
 * Un abbonamento con ciò che ne deriva oggi.
 *
 * @param nextChargeDate il primo addebito da oggi compreso; assente se è finito
 * @param monthlyCents   il costo di un mese medio, per confrontare cadenze diverse
 * @param yearlyCents    il costo di un anno medio
 * @param categoryId     assente se non scelta
 * @param accountId      assente se non scelto
 */
public record SubscriptionResponse(
        String id,
        String name,
        long amountCents,
        String currencyCode,
        int every,
        String unit,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        LocalDate nextChargeDate,
        long monthlyCents,
        long yearlyCents,
        String categoryId,
        String accountId) {

    static SubscriptionResponse from(Subscription subscription, LocalDate today) {
        return new SubscriptionResponse(
                subscription.id().toString(),
                subscription.name(),
                subscription.amount().amount(),
                subscription.amount().currency().name(),
                subscription.cadence().every(),
                KebabCase.from(subscription.cadence().unit()),
                subscription.startDate(),
                subscription.endDate(),
                subscription.isActiveOn(today),
                subscription.nextChargeOn(today).orElse(null),
                subscription.monthlyCost().amount(),
                subscription.yearlyCost().amount(),
                subscription.categoryId() == null ? null : subscription.categoryId().toString(),
                subscription.accountId() == null ? null : subscription.accountId().toString());
    }
}
