package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.shared.money.Money;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Gli abbonamenti visti da {@code today}: quanto costano quelli attivi e quali
 * addebiti cadono da oggi a {@code until} compreso.
 *
 * I costi mensile e annuo sommano quelli dei singoli abbonamenti già arrotondati,
 * così il totale coincide con la somma delle righe che l'utente vede.
 */
public record SubscriptionsOverview(
        LocalDate today,
        LocalDate until,
        int activeCount,
        Money monthlyCost,
        Money yearlyCost,
        Money upcomingTotal,
        List<UpcomingCharge> upcoming) {

    public static SubscriptionsOverview of(List<Subscription> subscriptions, LocalDate today, LocalDate until) {
        List<Subscription> attivi = subscriptions.stream()
                .filter(abbonamento -> abbonamento.isActiveOn(today))
                .toList();
        List<UpcomingCharge> addebiti = attivi.stream()
                .flatMap(abbonamento -> abbonamento.chargesBetween(today, until).stream()
                        .map(data -> new UpcomingCharge(abbonamento, data)))
                .sorted(Comparator.comparing(UpcomingCharge::date)
                        .thenComparing(addebito -> addebito.subscription().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new SubscriptionsOverview(
                today,
                until,
                attivi.size(),
                Money.sum(attivi.stream().map(Subscription::monthlyCost).toList()),
                Money.sum(attivi.stream().map(Subscription::yearlyCost).toList()),
                Money.sum(addebiti.stream().map(addebito -> addebito.subscription().amount()).toList()),
                addebiti);
    }
}
