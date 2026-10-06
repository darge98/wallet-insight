package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.shared.money.Money;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Gli addebiti di un mese di calendario, per tutti gli abbonamenti che in quel
 * mese erano in corso: anche un disdetto vi compare, fino alla sua fine.
 */
public record MonthCharges(YearMonth month, Money total, List<UpcomingCharge> charges) {

    public static MonthCharges of(List<Subscription> subscriptions, YearMonth month) {
        List<UpcomingCharge> addebiti = subscriptions.stream()
                .flatMap(abbonamento -> abbonamento.chargesBetween(month.atDay(1), month.atEndOfMonth()).stream()
                        .map(data -> new UpcomingCharge(abbonamento, data)))
                .sorted(Comparator.comparing(UpcomingCharge::date)
                        .thenComparing(addebito -> addebito.subscription().name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new MonthCharges(
                month,
                Money.sum(addebiti.stream().map(addebito -> addebito.subscription().amount()).toList()),
                addebiti);
    }
}
