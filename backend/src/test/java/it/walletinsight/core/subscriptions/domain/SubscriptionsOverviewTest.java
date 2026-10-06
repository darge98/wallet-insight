package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionsOverviewTest {

    private static final UserId UTENTE = UserId.newId();
    private static final LocalDate OGGI = LocalDate.of(2026, 10, 5);

    @Test
    void sommaSoloGliAttiviEOrdinaGliAddebitiPerData() {
        Subscription spotify = SubscriptionFixtures.abbonamento(UTENTE, "Spotify", Money.of(1_199), Cadence.monthly(),
                LocalDate.of(2026, 3, 20), null);
        Subscription netflix = SubscriptionFixtures.abbonamento(UTENTE, "Netflix", Money.of(1_399), Cadence.monthly(),
                LocalDate.of(2026, 1, 8), null);
        Subscription disdetto = SubscriptionFixtures.abbonamento(UTENTE, "Disney+", Money.of(899), Cadence.monthly(),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 30));

        SubscriptionsOverview resoconto =
                SubscriptionsOverview.of(List.of(spotify, netflix, disdetto), OGGI, OGGI.plusDays(29));

        assertThat(resoconto.activeCount()).isEqualTo(2);
        assertThat(resoconto.monthlyCost()).isEqualTo(Money.of(2_598));
        assertThat(resoconto.yearlyCost()).isEqualTo(Money.of(31_176));
        assertThat(resoconto.upcoming()).extracting(addebito -> addebito.subscription().name())
                .containsExactly("Netflix", "Spotify");
        assertThat(resoconto.upcomingTotal()).isEqualTo(Money.of(2_598));
    }

    @Test
    void senzaAbbonamentiTuttoEZero() {
        SubscriptionsOverview resoconto = SubscriptionsOverview.of(List.of(), OGGI, OGGI.plusDays(6));

        assertThat(resoconto.monthlyCost()).isEqualTo(Money.zero());
        assertThat(resoconto.upcoming()).isEmpty();
    }
}
