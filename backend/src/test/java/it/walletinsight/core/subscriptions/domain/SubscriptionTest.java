package it.walletinsight.core.subscriptions.domain;

import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    private static final UserId UTENTE = UserId.newId();
    private static final LocalDate OGGI = LocalDate.of(2026, 10, 5);

    @Test
    void ilProssimoAddebitoEQuelloDiOggiSeCadeOggi() {
        Subscription netflix = abbonamento(1, CadenceUnit.MONTH, LocalDate.of(2026, 1, 5), null);

        assertThat(netflix.nextChargeOn(OGGI)).contains(OGGI);
        assertThat(netflix.nextChargeOn(OGGI.plusDays(1))).contains(LocalDate.of(2026, 11, 5));
    }

    @Test
    void unAbbonamentoDiFineMeseNonScivolaDopoFebbraio() {
        Subscription palestra = abbonamento(1, CadenceUnit.MONTH, LocalDate.of(2026, 1, 31), null);

        assertThat(palestra.chargesBetween(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30)))
                .containsExactly(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
    }

    @Test
    void untrimestraleSiContaDalPrimoAddebito() {
        Subscription assicurazione = abbonamento(3, CadenceUnit.MONTH, LocalDate.of(2025, 11, 20), null);

        assertThat(assicurazione.nextChargeOn(OGGI)).contains(LocalDate.of(2026, 11, 20));
    }

    @Test
    void unAbbonamentoCheDeveAncoraPartireEAttivoEPartePrimoAddebito() {
        Subscription futuro = abbonamento(1, CadenceUnit.YEAR, LocalDate.of(2027, 3, 1), null);

        assertThat(futuro.isActiveOn(OGGI)).isTrue();
        assertThat(futuro.nextChargeOn(OGGI)).contains(LocalDate.of(2027, 3, 1));
    }

    @Test
    void unAbbonamentoDisdettoNonHaAddebitiOltreLaFine() {
        Subscription disdetto = abbonamento(1, CadenceUnit.MONTH, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 10, 31));

        assertThat(disdetto.isActiveOn(OGGI)).isTrue();
        assertThat(disdetto.nextChargeOn(OGGI)).contains(LocalDate.of(2026, 10, 10));
        assertThat(disdetto.nextChargeOn(LocalDate.of(2026, 10, 11))).isEmpty();
        assertThat(disdetto.isActiveOn(LocalDate.of(2026, 11, 1))).isFalse();
    }

    @Test
    void unSettimanaleCompareUnaVoltaPerAddebito() {
        Subscription giornale = abbonamento(1, CadenceUnit.WEEK, LocalDate.of(2026, 9, 28), null);

        assertThat(giornale.chargesBetween(OGGI, OGGI.plusDays(29))).containsExactly(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2));
    }

    @Test
    void iCostiEquivalentiSiArrotondanoUnaVoltaSola() {
        assertThat(abbonamento(1, CadenceUnit.MONTH, OGGI, null, 1_399).yearlyCost()).isEqualTo(Money.of(16_788));
        assertThat(abbonamento(3, CadenceUnit.MONTH, OGGI, null, 3_000).monthlyCost()).isEqualTo(Money.of(1_000));
        assertThat(abbonamento(1, CadenceUnit.YEAR, OGGI, null, 9_999).monthlyCost()).isEqualTo(Money.of(833));
        // 500 × 365,25 / 7 = 26.089,29: un anno ha un po' più di 52 settimane.
        assertThat(abbonamento(1, CadenceUnit.WEEK, OGGI, null, 500).yearlyCost()).isEqualTo(Money.of(26_089));
        assertThat(abbonamento(1, CadenceUnit.WEEK, OGGI, null, 500).monthlyCost()).isEqualTo(Money.of(2_174));
    }

    @Test
    void laFineNonPuoPrecedereIlPrimoAddebito() {
        assertThatThrownBy(() -> abbonamento(1, CadenceUnit.MONTH, OGGI, OGGI.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laCadenzaVaDaUnoANovantanove() {
        assertThatThrownBy(() -> new Cadence(0, CadenceUnit.MONTH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cadence(100, CadenceUnit.MONTH)).isInstanceOf(IllegalArgumentException.class);
    }

    static Subscription abbonamento(int every, CadenceUnit unit, LocalDate start, LocalDate end) {
        return abbonamento(every, unit, start, end, 1_000);
    }

    static Subscription abbonamento(int every, CadenceUnit unit, LocalDate start, LocalDate end, long cents) {
        return SubscriptionFixtures.abbonamento(UTENTE, "Prova", Money.of(cents), new Cadence(every, unit), start, end);
    }
}
