package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.walletinsight.core.ingestion.domain.RejectedCredentialsException;
import it.walletinsight.core.ingestion.domain.SourceUnavailableException;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordsPageDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BudgetBakersClientTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://bb.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BudgetBakersClient client = new BudgetBakersClient(
            builder.build(), new ObjectMapper(), new BudgetBakersProperties("https://bb.test", 200));

    @Test
    void un401DiventaCredenzialiRifiutate() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Unauthorized"));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(RejectedCredentialsException.class);
    }

    @Test
    void unErroreDellaSorgenteLaRendeNonDisponibile() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(SourceUnavailableException.class);
    }

    @Test
    void un429DiceQuantoAspettare() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "600"));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessage("BudgetBakers ha ricevuto troppe richieste da questo token: riprova fra 10 minuti.");
    }

    @Test
    void un429SenzaRetryAfterLeggibileRestaGenerico() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessage("BudgetBakers ha ricevuto troppe richieste da questo token: riprova più tardi.");
    }

    @Test
    void unaRichiestaRifiutataPerAltroRestaUnErroreGenerico() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(RejectedCredentialsException.class);
    }

    @Test
    void unTimeoutDiventaUnErroreLeggibile() {
        server.expect(request -> { }).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessage("BudgetBakers non è raggiungibile o non ha risposto in tempo.");
    }

    @Test
    void ilCorpoDiUnaRispostaDErroreRestaNeiLogETroncato() {
        server.expect(request -> { }).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("x".repeat(2_000)));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(SourceUnavailableException.class)
                .hasMessageNotContaining("xxx")
                .cause().message().hasSizeLessThan(600);
    }

    @Test
    void unaFinestraRistrettaDallaSorgenteFaFallireLaLettura() {
        var pagina = new RecordsPageDto(List.of(), 200, 0, null, 0, List.of("gte.2026-07-04T00:00:00.000Z"));

        assertThatThrownBy(() -> BudgetBakersClient.requireRequestedWindow(pagina, LocalDate.of(2026, 6, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ristretto");
    }

    @Test
    void laFinestraChiestaPassa() {
        var pagina = new RecordsPageDto(List.of(), 200, 0, null, 0,
                List.of("gte.2026-07-03T22:00:00.000Z", "lt.2026-10-05T22:00:00.000Z"));

        assertThatCode(() -> BudgetBakersClient.requireRequestedWindow(pagina, LocalDate.of(2026, 7, 4)))
                .doesNotThrowAnyException();
    }

    @Test
    void iContiSiLeggonoSeguendoLaPaginazione() {
        server.expect(queryParam("offset", "0"))
                .andRespond(json("""
                        {"accounts": [{"id": "acc-1"}], "limit": 200, "offset": 0, "nextOffset": 200}"""));
        server.expect(queryParam("offset", "200"))
                .andRespond(json("""
                        {"accounts": [{"id": "acc-2"}], "limit": 200, "offset": 200, "total": 2}"""));

        assertThat(client.accounts(TOKEN)).extracting("id").containsExactly("acc-1", "acc-2");
        server.verify();
    }

    @Test
    void unaPaginazioneCheNonAvanzaSiFerma() {
        // Senza il controllo il ciclo chiederebbe la stessa pagina all'infinito.
        server.expect(queryParam("offset", "0"))
                .andRespond(json("""
                        {"accounts": [{"id": "acc-1"}], "limit": 200, "offset": 0, "nextOffset": 0}"""));

        assertThatThrownBy(() -> client.accounts(TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Paginazione ferma: nextOffset 0 non supera l'offset 0");
    }

    @Test
    void leCategorieChiedonoUnaPaginaConLimitEsplicito() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://bb.test/v1/api/categories")))
                .andExpect(queryParam("limit", "200"))
                .andExpect(queryParam("withTotal", "true"))
                .andRespond(json("""
                        {"categories": [{"id": "cat-1", "name": "Ristoranti"}], "limit": 200, "offset": 0}"""));

        assertThat(client.categories(TOKEN)).extracting("name").containsExactly("Ristoranti");
    }

    @Test
    void leCategorieSenzaIlCampoAttesoFannoFallireLaLettura() {
        server.expect(queryParam("offset", "0")).andRespond(json("""
                {"limit": 200, "offset": 0}"""));

        assertThatThrownBy(() -> client.categories(TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nessun campo 'categories'");
    }

    @Test
    void iMovimentiChiedonoLaFinestraConEntrambiGliEstremi() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://bb.test/v1/api/records")))
                .andExpect(queryParam("recordDate", "gte.2026-09-01", "lt.2026-10-01"))
                .andExpect(queryParam("offset", "0"))
                .andRespond(json("""
                        {"records": [], "limit": 200, "offset": 0, "total": 0}"""));

        assertThat(client.recordsBetween(TOKEN, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1))).isEmpty();
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }
}
