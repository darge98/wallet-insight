package it.walletinsight.platform.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KebabCaseTest {

    enum Metodo { CARD, DIRECT_DEBIT, DIGITAL_WALLET }

    @Test
    void traduceUnaCostanteSempliceInMinuscolo() {
        assertThat(KebabCase.from(Metodo.CARD)).isEqualTo("card");
    }

    @Test
    void traduceGliUnderscoreInTrattini() {
        assertThat(KebabCase.from(Metodo.DIRECT_DEBIT)).isEqualTo("direct-debit");
        assertThat(KebabCase.from(Metodo.DIGITAL_WALLET)).isEqualTo("digital-wallet");
    }

    @Test
    void ricostruisceLaCostanteDalTrattino() {
        assertThat(KebabCase.to(Metodo.class, "direct-debit")).isEqualTo(Metodo.DIRECT_DEBIT);
        assertThat(KebabCase.to(Metodo.class, "card")).isEqualTo(Metodo.CARD);
    }

    @Test
    void rifiutaUnValoreSconosciuto() {
        assertThatThrownBy(() -> KebabCase.to(Metodo.class, "assegno"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assegno");
    }

    @Test
    void faLaViaEIlRitornoSenzaPerdite() {
        for (Metodo valore : Metodo.values()) {
            assertThat(KebabCase.to(Metodo.class, KebabCase.from(valore))).isEqualTo(valore);
        }
    }
}
