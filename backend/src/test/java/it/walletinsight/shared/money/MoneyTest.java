package it.walletinsight.shared.money;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void converteLeUnitaMaggioriInCentesimiInteri() {
        assertThat(Money.fromMajor(12.34)).isEqualTo(Money.of(1234));
        assertThat(Money.fromMajor(0.1)).isEqualTo(Money.of(10));
    }

    @Test
    void evitaGliErroriDiArrotondamentoDelFloatingPoint() {
        Money totale = Money.sum(List.of(Money.fromMajor(0.1), Money.fromMajor(0.2)));

        assertThat(totale.amount()).isEqualTo(30);
    }

    @Test
    void sommaESottraeMantenendoLaValuta() {
        assertThat(Money.of(500).plus(Money.of(250))).isEqualTo(Money.of(750));
        assertThat(Money.of(500).minus(Money.of(250))).isEqualTo(Money.of(250));
    }

    @Test
    void rifiutaLeOperazioniFraValuteDiverse() {
        Money euro = Money.of(100, CurrencyCode.EUR);
        Money dollari = Money.of(100, CurrencyCode.USD);

        assertThatThrownBy(() -> euro.plus(dollari))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void negaEConfronta() {
        assertThat(Money.of(120).negated().amount()).isEqualTo(-120);
        assertThat(Money.of(120)).isGreaterThan(Money.of(100));
    }

    @Test
    void restituisceZeroComeRapportoQuandoIlDenominatoreENullo() {
        assertThat(Money.of(100).ratioTo(Money.zero())).isEqualTo(0.0);
        assertThat(Money.of(50).ratioTo(Money.of(200))).isEqualTo(0.25);
    }

    @Test
    void sommaUnaListaVuotaSenzaFallire() {
        assertThat(Money.sum(List.of())).isEqualTo(Money.zero());
    }
}
