package it.walletinsight.core.ingestion.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalTokenTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234";

    @Test
    void accettaUnJwtCompattoEToglieGliSpazi() {
        assertThat(new PersonalToken("  " + JWT + "  ").value()).isEqualTo(JWT);
    }

    @Test
    void rifiutaUnTokenVuoto() {
        assertThatThrownBy(() -> new PersonalToken("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obbligatorio");
    }

    @Test
    void rifiutaUnTokenCheNonEUnJwt() {
        assertThatThrownBy(() -> new PersonalToken("incollato-a-meta"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT");
    }

    @Test
    void rifiutaUnTokenTroppoLungo() {
        String lungo = "a".repeat(PersonalToken.MAX_LENGTH) + ".b.c";

        assertThatThrownBy(() -> new PersonalToken(lungo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(PersonalToken.MAX_LENGTH));
    }

    @Test
    void laTracciaMostraSoloLeUltimeQuattroCifre() {
        assertThat(new PersonalToken(JWT).hint()).isEqualTo("…1234");
    }

    @Test
    void toStringNonRivelaIlSegreto() {
        // Un record stamperebbe il token per intero in ogni log che incontra l'aggregato.
        assertThat(new PersonalToken(JWT)).hasToString("PersonalToken[…1234]");
    }
}
