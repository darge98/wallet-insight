package it.walletinsight.bff.imports;

import it.walletinsight.core.ingestion.application.ImportConnectionService;
import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionId;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.platform.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Contratto HTTP del modulo: forme JSON, kebab-case, ProblemDetail e codici di stato.
 * Il servizio è mockkato: qui si verifica solo il layer `api` più la advice di platform.
 */
@WebMvcTest(ImportConnectionsController.class)
class ImportConnectionsControllerTest {

    private static final UserId UTENTE =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234";
    private static final ImportConnection CONNESSIONE = new ImportConnection(
            ImportConnectionId.newId(),
            UTENTE,
            IngestionSource.BUDGET_BAKERS,
            new PersonalToken(TOKEN),
            true,
            LocalDate.of(2026, 9, 21));

    private static final String URL = "/api/users/" + UTENTE + "/import-connections";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private ImportConnectionService service;

    @MockitoBean
    private ImportService imports;

    @Test
    void elencaLeSorgentiCollegate() {
        given(service.listConnections(UTENTE)).willReturn(List.of(CONNESSIONE));

        MvcTestResultAssert risposta = assertThat(mockMvc.get().uri(URL));

        risposta.hasStatusOk();
        risposta.bodyJson().extractingPath("$[0].source").isEqualTo("budget-bakers");
        risposta.bodyJson().extractingPath("$[0].enabled").isEqualTo(true);
        risposta.bodyJson().extractingPath("$[0].secretHint").isEqualTo("…1234");
        risposta.bodyJson().extractingPath("$[0].configuredAt").isEqualTo("2026-09-21");
    }

    @Test
    void ilSegretoNonTornaMaiNellaRisposta() {
        given(service.listConnections(UTENTE)).willReturn(List.of(CONNESSIONE));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyText().doesNotContain(TOKEN);
    }

    @Test
    void collegaUnaSorgenteConIlTokenPersonale() {
        given(service.configure(eq(UTENTE), eq(IngestionSource.BUDGET_BAKERS), any(), anyBoolean()))
                .willReturn(CONNESSIONE);
        given(service.listConnections(UTENTE)).willReturn(List.of(CONNESSIONE));

        MvcTestResultAssert risposta = assertThat(mockMvc.put().uri(URL + "/budget-bakers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + TOKEN + "\"}"));

        risposta.hasStatusOk();
        risposta.bodyJson().extractingPath("$.source").isEqualTo("budget-bakers");
        // `enabled` assente nel corpo: collegare una sorgente la attiva.
        verify(service).configure(UTENTE, IngestionSource.BUDGET_BAKERS,
                new PersonalToken(TOKEN), true);
    }

    @Test
    void unTokenNuovoFaPartireSubitoLImportDellaSorgente() {
        given(service.configure(eq(UTENTE), eq(IngestionSource.BUDGET_BAKERS), any(), anyBoolean()))
                .willReturn(CONNESSIONE);
        given(service.listConnections(UTENTE)).willReturn(List.of(CONNESSIONE));

        assertThat(mockMvc.put().uri(URL + "/budget-bakers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + TOKEN + "\"}")).hasStatusOk();

        verify(imports).importFor(UTENTE, IngestionSource.BUDGET_BAKERS);
    }

    @Test
    void unTokenRifiutatoSiVedeNellaRisposta() {
        ImportConnection rifiutata = CONNESSIONE.credentialsRejected(Instant.parse("2026-10-04T03:00:00Z"));
        given(service.listConnections(UTENTE)).willReturn(List.of(rifiutata));

        assertThat(mockMvc.get().uri(URL)).hasStatusOk()
                .bodyJson().extractingPath("$[0].credentialsRejectedAt").isEqualTo("2026-10-04T03:00:00Z");
    }

    @Test
    void rifiutaUnaConfigurazioneSenzaToken() {
        MvcTestResultAssert risposta = assertThat(mockMvc.put().uri(URL + "/budget-bakers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.errors[0].field").isEqualTo("token");
        verifyNoInteractions(service);
    }

    @Test
    void rifiutaUnTokenCheNonEUnJwt() {
        MvcTestResultAssert risposta = assertThat(mockMvc.put().uri(URL + "/budget-bakers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"incollato-a-meta\"}"));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.detail").isEqualTo("Il token personale non è un JWT valido.");
        verifyNoInteractions(service);
    }

    @Test
    void rifiutaUnaSorgenteSconosciuta() {
        MvcTestResultAssert risposta = assertThat(mockMvc.put().uri(URL + "/carta-perforata")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + TOKEN + "\"}"));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.title").isEqualTo("Richiesta non valida");
        verifyNoInteractions(service);
    }

    @Test
    void unUtenteInesistenteEUn404() {
        given(service.listConnections(UTENTE))
                .willThrow(new ResourceNotFoundException("Utente", UTENTE.toString()));

        MvcTestResultAssert risposta = assertThat(mockMvc.get().uri(URL));

        risposta.hasStatus(404);
        risposta.bodyJson().extractingPath("$.resourceType").isEqualTo("Utente");
    }

    @Test
    void scollegaUnaSorgente() {
        assertThat(mockMvc.delete().uri(URL + "/budget-bakers")).hasStatus(204);

        verify(service).disconnect(UTENTE, IngestionSource.BUDGET_BAKERS);
    }

    @Test
    void scollegareUnaSorgenteMaiCollegataEUn404() {
        willThrow(new ResourceNotFoundException("Connessione di importazione", "budget-bakers"))
                .given(service).disconnect(UTENTE, IngestionSource.BUDGET_BAKERS);

        assertThat(mockMvc.delete().uri(URL + "/budget-bakers")).hasStatus(404);
    }
}
