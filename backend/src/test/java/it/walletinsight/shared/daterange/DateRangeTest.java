package it.walletinsight.shared.daterange;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final DateRange SETTEMBRE =
            new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @Test
    void includeGliEstremi() {
        assertThat(SETTEMBRE.contains(LocalDate.of(2026, 9, 1))).isTrue();
        assertThat(SETTEMBRE.contains(LocalDate.of(2026, 9, 30))).isTrue();
        assertThat(SETTEMBRE.contains(LocalDate.of(2026, 10, 1))).isFalse();
    }

    @Test
    void contaIGiorniIncludendoEntrambiGliEstremi() {
        assertThat(SETTEMBRE.days()).isEqualTo(30L);
    }

    @Test
    void unIntervalloDiUnSoloGiornoDuraUnGiorno() {
        LocalDate giorno = LocalDate.of(2026, 9, 13);

        assertThat(new DateRange(giorno, giorno).days()).isEqualTo(1L);
    }

    @Test
    void unInizioDiMeseSiConfrontaConLoStessoTrattoDelMesePrima() {
        var ottobreFinora = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4));

        assertThat(ottobreFinora.previousComparable())
                .isEqualTo(new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)));
    }

    @Test
    void unMeseInteroSiConfrontaColMeseInteroPrima() {
        var marzo = new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        var aprile = new DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(marzo.previousComparable())
                .isEqualTo(new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        assertThat(aprile.previousComparable())
                .isEqualTo(new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
    }

    @Test
    void unTrattoPiuLungoDelMesePrimaSiFermaAllaSuaFine() {
        var finoAl30Marzo = new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30));

        assertThat(finoAl30Marzo.previousComparable())
                .isEqualTo(new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
    }

    @Test
    void gliAltriIntervalliSiConfrontanoConAltrettantiGiorniPrima() {
        var ultimiSette = new DateRange(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 8));
        var aCavallo = new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 4));

        assertThat(ultimiSette.previousComparable())
                .isEqualTo(new DateRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1)));
        assertThat(aCavallo.previousComparable().to()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void rifiutaUnIntervalloRovesciato() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
