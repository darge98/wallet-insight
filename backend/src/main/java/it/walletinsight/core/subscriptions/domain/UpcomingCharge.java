package it.walletinsight.core.subscriptions.domain;

import java.time.LocalDate;

/** Un addebito previsto: quale abbonamento, e quando. */
public record UpcomingCharge(Subscription subscription, LocalDate date) {
}
