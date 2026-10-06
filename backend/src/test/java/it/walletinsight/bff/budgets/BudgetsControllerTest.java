package it.walletinsight.bff.budgets;

import it.walletinsight.core.budgets.application.BudgetService;
import it.walletinsight.core.budgets.domain.Budget;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.movements.application.MovementService;
import it.walletinsight.core.movements.domain.CategorySpending;
import it.walletinsight.core.movements.domain.MovementTotals;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(BudgetsController.class)
class BudgetsControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final String URL = "/api/users/" + UTENTE + "/budgets";
    private static final YearMonth OTTOBRE = YearMonth.of(2026, 10);

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private BudgetService budgets;

    @MockitoBean
    private MovementService movements;

    @MockitoBean
    private CategoryService categories;

    private final Category ristoranti = categoria("Ristoranti");
    private final Category spesa = categoria("Spesa");
    private Budget cibo;
    private Budget fuori;

    @BeforeEach
    void prepara() {
        cibo = Budget.create(UTENTE, null, "Cibo", Set.of(ristoranti.id(), spesa.id()), OTTOBRE, Money.of(50_000));
        fuori = Budget.create(UTENTE, cibo.id(), "Ristoranti", Set.of(ristoranti.id()), OTTOBRE, Money.of(20_000));
        given(budgets.currentMonth(UTENTE)).willReturn(OTTOBRE);
        given(categories.listCategories(UTENTE)).willReturn(List.of(ristoranti, spesa));
    }

    @Test
    void ilMeseMetteIlSottoBudgetDentroIlPrincipaleEDiceQuantoEFuoriBudget() {
        given(budgets.listBudgets(UTENTE)).willReturn(List.of(cibo, fuori));
        given(movements.expensesByCategory(eq(UTENTE), any(), anyInt())).willReturn(List.of(
                new CategorySpending(ristoranti.id(), Money.of(24_000), 6),
                new CategorySpending(spesa.id(), Money.of(15_000), 4)));
        given(movements.totals(eq(UTENTE), any())).willReturn(
                new MovementTotals(Money.of(200_000), Money.of(60_000), Money.of(140_000), 30));

        assertThat(mockMvc.get().uri(URL + "/month?month=2026-10")).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {
                          "month": "2026-10",
                          "currencyCode": "EUR",
                          "limitCents": 50000,
                          "spentCents": 39000,
                          "remainingCents": 11000,
                          "unbudgetedCents": 21000,
                          "expensesCents": 60000,
                          "budgets": [{
                            "name": "Cibo",
                            "limitCents": 50000,
                            "spentCents": 39000,
                            "categories": [
                              {"name": "Ristoranti", "spentCents": 24000},
                              {"name": "Spesa", "spentCents": 15000}
                            ],
                            "children": [{
                              "name": "Ristoranti",
                              "limitCents": 20000,
                              "spentCents": 24000,
                              "remainingCents": -4000
                            }]
                          }]
                        }""");
    }

    @Test
    void unMeseFuoriFormatoEUnaRichiestaSbagliata() {
        assertThat(mockMvc.get().uri(URL + "/month?month=ottobre")).hasStatus(400);
    }

    @Test
    void creareUnSottoBudgetRispondeConLaDefinizione() {
        given(budgets.createBudget(any(), any(), any(), any(), any())).willReturn(fuori);

        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Ristoranti", "parentId": "%s", "categoryIds": ["%s"], "limitCents": 20000}"""
                        .formatted(cibo.id(), ristoranti.id())))
                .hasStatus(201)
                .bodyJson().isLenientlyEqualTo("""
                        {"name": "Ristoranti", "parentId": "%s", "categoryIds": ["%s"], "limitCents": 20000}"""
                        .formatted(cibo.id(), ristoranti.id()));

        then(budgets).should().createBudget(UTENTE, cibo.id(), "Ristoranti", Set.of(ristoranti.id()),
                Money.of(20_000));
    }

    @Test
    void unBudgetSenzaCategorieOSenzaLimiteNonSiCrea() {
        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Cibo", "categoryIds": [], "limitCents": 20000}""")).hasStatus(400);
        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Cibo", "categoryIds": ["%s"]}""".formatted(ristoranti.id()))).hasStatus(400);
    }

    @Test
    void ilPatchPassaNullPerICampiAssenti() {
        given(budgets.updateBudget(any(), any(), any(), any(), any())).willReturn(cibo);

        assertThat(mockMvc.patch().uri(URL + "/" + cibo.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"limitCents": 60000}""")).hasStatusOk();

        then(budgets).should().updateBudget(UTENTE, cibo.id(), null, null, Money.of(60_000));
    }

    @Test
    void unaRegolaFraBudgetViolataEUnConflitto() {
        given(budgets.updateBudget(any(), any(), any(), any(), any()))
                .willThrow(new IllegalStateException("Una delle categorie scelte è già nel budget «Cibo»."));

        assertThat(mockMvc.patch().uri(URL + "/" + fuori.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Altro"}""")).hasStatus(409)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Una delle categorie scelte è già nel budget «Cibo».");
    }

    @Test
    void cancellareRispondeSenzaCorpo() {
        assertThat(mockMvc.delete().uri(URL + "/" + cibo.id())).hasStatus(204);

        then(budgets).should().deleteBudget(UTENTE, cibo.id());
    }

    private static Category categoria(String nome) {
        return Category.create(UTENTE, CategoryId.newId(), nome);
    }
}
