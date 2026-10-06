package it.walletinsight.bff.analytics;

import it.walletinsight.core.accounts.application.AccountService;
import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.ConvertedMovements;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovementService movements;

    @MockitoBean
    private CategoryService categories;

    @MockitoBean
    private AccountService accounts;

    @Test
    void ilPatrimonioEInEuroELasciaFuoriGliArchiviati() {
        Account credem = conto("acc-1", CurrencyCode.EUR, 100_000L, false);
        Account dollari = conto("acc-2", CurrencyCode.USD, 10_000L, false);
        Account archiviato = conto("acc-3", CurrencyCode.EUR, 999_999L, true);
        given(accounts.listAccounts(UTENTE)).willReturn(List.of(credem, dollari, archiviato));
        given(movements.convertedByAccount(UTENTE)).willReturn(Map.of(
                credem.id(), new ConvertedMovements(Money.of(5_000L), BigDecimal.ONE),
                dollari.id(), new ConvertedMovements(Money.of(-4_500L), new BigDecimal("0.9"))));

        // 1000 € + 50 € sul conto in euro; 100 $ al cambio del primo movimento (90 €)
        // meno 45 € di movimenti sul conto in dollari.
        assertThat(mockMvc.get().uri("/api/users/" + UTENTE + "/analytics/net-worth"))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"netWorthCents": 109500, "currencyCode": "EUR"}
                        """);
    }

    private static Account conto(String externalId, CurrencyCode valuta, long iniziale, boolean archiviato) {
        return Account.imported(UTENTE, IngestionSource.BUDGET_BAKERS, externalId, externalId,
                AccountKind.CASH, valuta, Money.of(iniziale, valuta), null, archiviato, false);
    }
}
