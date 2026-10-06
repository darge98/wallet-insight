package it.walletinsight.core.accounts.domain;

import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La regola che conta: il riferimento all'originale non si muove mai, e una
 * rinomina dell'utente sopravvive a tutti gli import successivi.
 */
class AccountTest {

    private static final UserId UTENTE = UserId.newId();
    private static final String ESTERNO = "277f06aa-f852-4da5-b701-434f3919d3f7";

    @Test
    void unContoImportatoPrendeIlNomeDallaSorgente() {
        Account conto = importato("Credem");

        assertThat(conto.id().value().version()).isEqualTo(7);
        assertThat(conto.name()).isEqualTo("Credem");
        assertThat(conto.sourceName()).isEqualTo("Credem");
        assertThat(conto.renamedByUser()).isFalse();
        assertThat(conto.externalId()).isEqualTo(ESTERNO);
    }

    @Test
    void finchePerLUtenteVaBeneIlNomeSegueLaSorgente() {
        Account conto = importato("Credem");

        Account riallineato = conto.refreshedFrom(importato("Credem Banca"));

        assertThat(riallineato.name()).isEqualTo("Credem Banca");
        assertThat(riallineato.sourceName()).isEqualTo("Credem Banca");
        assertThat(riallineato.renamedByUser()).isFalse();
    }

    @Test
    void unaRinominaDellUtenteSopravviveAllImport() {
        Account rinominato = importato("Credem").renamedTo("Conto stipendio");

        Account riallineato = rinominato.refreshedFrom(importato("Credem Banca"));

        // Il nome dell'utente resta; quello della sorgente si aggiorna sotto.
        assertThat(riallineato.name()).isEqualTo("Conto stipendio");
        assertThat(riallineato.sourceName()).isEqualTo("Credem Banca");
        assertThat(riallineato.renamedByUser()).isTrue();
    }

    @Test
    void unaRinominaSopravviveAncheAImportRipetuti() {
        Account rinominato = importato("Credem").renamedTo("Conto stipendio");

        Account dopoTre = rinominato
                .refreshedFrom(importato("Credem"))
                .refreshedFrom(importato("Credem"))
                .refreshedFrom(importato("Credem"));

        assertThat(dopoTre.name()).isEqualTo("Conto stipendio");
    }

    @Test
    void ilRiferimentoAllOriginaleNonSiMuoveMai() {
        Account conto = importato("Credem");

        Account riallineato = conto.refreshedFrom(new Account(
                AccountId.newId(), UTENTE, IngestionSource.BUDGET_BAKERS,
                "un-altro-id", "Altro", "Altro", AccountKind.CASH,
                CurrencyCode.EUR, Money.of(0L), null, null, false, false));

        assertThat(riallineato.id()).isEqualTo(conto.id());
        assertThat(riallineato.externalId()).isEqualTo(ESTERNO);
        assertThat(riallineato.source()).isEqualTo(IngestionSource.BUDGET_BAKERS);
        // Il resto invece segue la sorgente: e' un fatto suo, non nostro.
        assertThat(riallineato.kind()).isEqualTo(AccountKind.CASH);
    }

    @Test
    void ilColoreSceltoDallUtenteSopravviveAllImport() {
        // La sorgente non sa nemmeno che esista: e' dell'utente come il nome.
        Account colorato = importato("Credem").coloredWith("#f97316");

        assertThat(colorato.refreshedFrom(importato("Credem Banca")).color()).isEqualTo("#f97316");
    }

    @Test
    void unContoImportatoNonHaUnColoreDeciso() {
        // Assegnarne uno d'ufficio lo renderebbe indistinguibile da una scelta vera.
        assertThat(importato("Credem").color()).isNull();
    }

    @Test
    void ilColoreSiNormalizzaInMinuscolo() {
        // '#FF8800' e '#ff8800' sono lo stesso colore: tenerli distinti farebbe
        // risultare "cambiato" un conto che nessuno ha toccato.
        assertThat(importato("Credem").coloredWith("#FF8800").color()).isEqualTo("#ff8800");
    }

    @Test
    void unColoreFuoriFormatoNonEntra() {
        assertThatThrownBy(() -> importato("Credem").coloredWith("arancione"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("#rrggbb");
    }

    @Test
    void ilSaldoInizialeDeveEssereNellaValutaDelConto() {
        assertThatThrownBy(() -> new Account(
                AccountId.newId(), UTENTE, IngestionSource.BUDGET_BAKERS, ESTERNO,
                "Credem", "Credem", AccountKind.CURRENT_ACCOUNT,
                CurrencyCode.EUR, Money.of(100L, CurrencyCode.USD), null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void unIbanAssenteRestaNullNonStringaVuota() {
        Account contanti = Account.imported(
                UTENTE, IngestionSource.BUDGET_BAKERS, "cash-1", "Contanti",
                AccountKind.CASH, CurrencyCode.EUR, Money.of(10_000L), "   ", false, false);

        assertThat(contanti.iban()).isNull();
    }

    private static Account importato(String nomeNellaSorgente) {
        return Account.imported(
                UTENTE, IngestionSource.BUDGET_BAKERS, ESTERNO, nomeNellaSorgente,
                AccountKind.CURRENT_ACCOUNT, CurrencyCode.EUR,
                Money.of(892_108L), "F010000712861", false, false);
    }
}
