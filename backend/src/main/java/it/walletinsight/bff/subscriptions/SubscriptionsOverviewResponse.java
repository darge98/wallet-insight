package it.walletinsight.bff.subscriptions;

import it.walletinsight.core.subscriptions.domain.SubscriptionsOverview;
import it.walletinsight.core.subscriptions.domain.UpcomingCharge;

import java.time.LocalDate;
import java.util.List;

/**
 * Quanto costano gli abbonamenti attivi e cosa verrà addebitato da {@code today} a
 * {@code until} compreso. Un abbonamento settimanale compare una volta per addebito.
 */
public record SubscriptionsOverviewResponse(
        LocalDate today,
        LocalDate until,
        String currencyCode,
        int activeCount,
        long monthlyCents,
        long yearlyCents,
        long upcomingCents,
        List<UpcomingChargeResponse> upcoming) {

    public record UpcomingChargeResponse(String subscriptionId, String name, LocalDate date, long amountCents) {

        static UpcomingChargeResponse from(UpcomingCharge charge) {
            return new UpcomingChargeResponse(
                    charge.subscription().id().toString(),
                    charge.subscription().name(),
                    charge.date(),
                    charge.subscription().amount().amount());
        }
    }

    static SubscriptionsOverviewResponse from(SubscriptionsOverview overview) {
        return new SubscriptionsOverviewResponse(
                overview.today(),
                overview.until(),
                overview.monthlyCost().currency().name(),
                overview.activeCount(),
                overview.monthlyCost().amount(),
                overview.yearlyCost().amount(),
                overview.upcomingTotal().amount(),
                overview.upcoming().stream().map(UpcomingChargeResponse::from).toList());
    }
}
