package it.walletinsight.bff.subscriptions;

import it.walletinsight.bff.subscriptions.SubscriptionsOverviewResponse.UpcomingChargeResponse;
import it.walletinsight.core.subscriptions.domain.MonthCharges;

import java.util.List;

/**
 * Gli addebiti di un mese, per il calendario dei rinnovi.
 *
 * @param month `yyyy-MM`
 */
public record MonthChargesResponse(
        String month,
        String currencyCode,
        long totalCents,
        List<UpcomingChargeResponse> charges) {

    static MonthChargesResponse from(MonthCharges month) {
        return new MonthChargesResponse(
                month.month().toString(),
                month.total().currency().name(),
                month.total().amount(),
                month.charges().stream().map(UpcomingChargeResponse::from).toList());
    }
}
