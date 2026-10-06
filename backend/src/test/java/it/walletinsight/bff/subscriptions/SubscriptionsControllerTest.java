package it.walletinsight.bff.subscriptions;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.subscriptions.application.SubscriptionService;
import it.walletinsight.core.subscriptions.domain.MonthCharges;
import it.walletinsight.core.subscriptions.domain.SubscriptionDefinition;
import it.walletinsight.core.subscriptions.domain.Cadence;
import it.walletinsight.core.subscriptions.domain.CadenceUnit;
import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.core.subscriptions.domain.SubscriptionFixtures;
import it.walletinsight.core.subscriptions.domain.SubscriptionsOverview;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(SubscriptionsController.class)
class SubscriptionsControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final String URL = "/api/users/" + UTENTE + "/subscriptions";
    private static final LocalDate OGGI = LocalDate.of(2026, 10, 5);

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private SubscriptionService subscriptions;

    private Subscription netflix;
    private Subscription disneyDisdetto;
    private Subscription assicurazione;

    @BeforeEach
    void prepara() {
        netflix = SubscriptionFixtures.abbonamento(UTENTE, "Netflix", Money.of(1_399), Cadence.monthly(),
                LocalDate.of(2026, 1, 20), null);
        disneyDisdetto = SubscriptionFixtures.abbonamento(UTENTE, "Disney+", Money.of(899), Cadence.monthly(),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 30));
        assicurazione = SubscriptionFixtures.abbonamento(UTENTE, "Assicurazione", Money.of(12_000),
                new Cadence(3, CadenceUnit.MONTH), LocalDate.of(2025, 11, 10), null);
        given(subscriptions.today(UTENTE)).willReturn(OGGI);
    }

    @Test
    void lElencoMettePrimaGliAttiviDalProssimoAddebito() {
        given(subscriptions.listSubscriptions(UTENTE)).willReturn(List.of(assicurazione, disneyDisdetto, netflix));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [
                          {"name": "Netflix", "every": 1, "unit": "month", "active": true,
                           "nextChargeDate": "2026-10-20", "monthlyCents": 1399, "yearlyCents": 16788},
                          {"name": "Assicurazione", "every": 3, "unit": "month",
                           "nextChargeDate": "2026-11-10", "monthlyCents": 4000},
                          {"name": "Disney+", "active": false, "endDate": "2026-09-30"}
                        ]""");
    }

    @Test
    void ilResocontoPortaTotaliEAddebitiImminenti() {
        given(subscriptions.overview(UTENTE, 30)).willReturn(
                SubscriptionsOverview.of(List.of(netflix, assicurazione), OGGI, OGGI.plusDays(29)));

        assertThat(mockMvc.get().uri(URL + "/overview")).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {
                          "today": "2026-10-05",
                          "until": "2026-11-03",
                          "currencyCode": "EUR",
                          "activeCount": 2,
                          "monthlyCents": 5399,
                          "upcomingCents": 1399,
                          "upcoming": [{"name": "Netflix", "date": "2026-10-20", "amountCents": 1399}]
                        }""");
    }

    @Test
    void ilCalendarioPortaGliAddebitiDelMeseAncheDiUnDisdetto() {
        given(subscriptions.monthCharges(UTENTE, YearMonth.of(2026, 9))).willReturn(
                MonthCharges.of(List.of(netflix, disneyDisdetto), YearMonth.of(2026, 9)));

        assertThat(mockMvc.get().uri(URL + "/calendar?month=2026-09")).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {
                          "month": "2026-09",
                          "totalCents": 2298,
                          "charges": [
                            {"name": "Disney+", "date": "2026-09-01"},
                            {"name": "Netflix", "date": "2026-09-20"}
                          ]
                        }""");
    }

    @Test
    void creareConCategoriaEContoLiPassaAlServizio() {
        UUID categoria = UUID.randomUUID();
        UUID conto = UUID.randomUUID();
        given(subscriptions.createSubscription(any(), any())).willReturn(netflix);

        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Netflix", "amountCents": 1399, "every": 1, "unit": "month",
                         "startDate": "2026-01-20", "categoryId": "%s", "accountId": "%s"}"""
                        .formatted(categoria, conto)))
                .hasStatus(201);

        then(subscriptions).should().createSubscription(UTENTE, new SubscriptionDefinition("Netflix",
                Money.of(1_399), Cadence.monthly(), LocalDate.of(2026, 1, 20), null,
                CategoryId.of(categoria), AccountId.of(conto)));
    }

    @Test
    void creareRispondeConLAbbonamentoEIlProssimoAddebito() {
        given(subscriptions.createSubscription(any(), any())).willReturn(assicurazione);

        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Assicurazione", "amountCents": 12000, "every": 3, "unit": "month",
                         "startDate": "2025-11-10"}"""))
                .hasStatus(201)
                .bodyJson().isLenientlyEqualTo("""
                        {"name": "Assicurazione", "nextChargeDate": "2026-11-10"}""");

        then(subscriptions).should().createSubscription(UTENTE, SubscriptionFixtures.definizione("Assicurazione",
                Money.of(12_000), new Cadence(3, CadenceUnit.MONTH), LocalDate.of(2025, 11, 10), null));
    }

    @Test
    void unaCadenzaSconosciutaOUnImportoMancanteSonoRichiesteSbagliate() {
        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Netflix", "amountCents": 1399, "every": 1, "unit": "day",
                         "startDate": "2026-01-20"}""")).hasStatus(400);
        assertThat(mockMvc.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Netflix", "every": 1, "unit": "month", "startDate": "2026-01-20"}"""))
                .hasStatus(400);
    }

    @Test
    void ilPutSenzaFineRiattiva() {
        given(subscriptions.updateSubscription(any(), any(), any())).willReturn(netflix);

        assertThat(mockMvc.put().uri(URL + "/" + disneyDisdetto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Disney+", "amountCents": 899, "every": 1, "unit": "month",
                         "startDate": "2025-01-01"}""")).hasStatusOk();

        then(subscriptions).should().updateSubscription(UTENTE, disneyDisdetto.id(),
                SubscriptionFixtures.definizione("Disney+", Money.of(899), Cadence.monthly(),
                        LocalDate.of(2025, 1, 1), null));
    }

    @Test
    void cancellareRispondeSenzaCorpo() {
        assertThat(mockMvc.delete().uri(URL + "/" + netflix.id())).hasStatus(204);

        then(subscriptions).should().deleteSubscription(UTENTE, netflix.id());
    }
}
