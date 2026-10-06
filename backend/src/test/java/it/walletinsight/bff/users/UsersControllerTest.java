package it.walletinsight.bff.users;

import it.walletinsight.core.users.application.UserService;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Contratto HTTP del modulo: forme JSON, kebab-case, ProblemDetail e codici di stato.
 * Il servizio è mockkato: qui si verifica solo il layer `api` più la advice di platform.
 */
@WebMvcTest(UsersController.class)
class UsersControllerTest {

    private static final UserId ID =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);
    private static final User MARTA = new User(ID, "Marta", "Rossi", "marta@esempio.it", IMPOSTAZIONI);

    private static final String CORPO_VALIDO = """
            {
              "firstName": "Marta",
              "lastName": "Rossi",
              "email": "marta@esempio.it",
              "timeZone": "Europe/Rome",
              "language": "it",
              "defaultDashboardPeriod": "current-month"
            }
            """;

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private UserService service;

    @Test
    void creaUnUtenteERestituisceCreatedConLaLocation() {
        given(service.createUser(any(), any(), any(), any())).willReturn(MARTA);

        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_VALIDO));

        risposta.hasStatus(201).hasHeader("Location", "/api/users/" + ID);
        risposta.bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        risposta.bodyJson().extractingPath("$.firstName").isEqualTo("Marta");
        risposta.bodyJson().extractingPath("$.language").isEqualTo("it");
        risposta.bodyJson().extractingPath("$.defaultDashboardPeriod").isEqualTo("current-month");
    }

    @Test
    void rifiutaUnaCreazioneSenzaNome() {
        String corpo = """
                {"timeZone": "Europe/Rome", "language": "it", "defaultDashboardPeriod": "current-month"}
                """;

        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.title").isEqualTo("Richiesta non valida");
        risposta.bodyJson().extractingPath("$.errors[0].field").isEqualTo("firstName");
        verifyNoInteractions(service);
    }

    @Test
    void rifiutaUnaLinguaFuoriContratto() {
        String corpo = """
                {
                  "firstName": "Marta",
                  "timeZone": "Europe/Rome",
                  "language": "de",
                  "defaultDashboardPeriod": "current-month"
                }
                """;

        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.type")
                .isEqualTo("https://margine.app/errors/invalid-request");
        risposta.bodyJson().extractingPath("$.detail").asString().contains("de");
        verifyNoInteractions(service);
    }

    @Test
    void rifiutaUnFusoOrarioInesistente() {
        // La validazione del fuso avviene dentro UserSettings, costruita dal DTO
        // prima ancora di chiamare il servizio.
        String corpo = """
                {
                  "firstName": "Marta",
                  "timeZone": "Marte/OlympusMons",
                  "language": "it",
                  "defaultDashboardPeriod": "current-month"
                }
                """;

        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.detail").asString().contains("Marte/OlympusMons");
        verifyNoInteractions(service);
    }

    @Test
    void restituisce404PerUnUtenteCheNonEsiste() {
        given(service.getUser(ID)).willThrow(new ResourceNotFoundException("Utente", ID.toString()));

        MvcTestResultAssert risposta = assertThat(mockMvc.get().uri("/api/users/{id}", ID));

        risposta.hasStatus(404);
        risposta.bodyJson().extractingPath("$.title").isEqualTo("Risorsa non trovata");
        risposta.bodyJson().extractingPath("$.resourceType").isEqualTo("Utente");
        risposta.bodyJson().extractingPath("$.resourceId").isEqualTo(ID.toString());
    }

    @Test
    void restituisce409QuandoLEmailEsisteGia() {
        given(service.createUser(any(), any(), any(), any()))
                .willThrow(new DuplicateKeyException("duplicato: marta@esempio.it"));

        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_VALIDO));

        risposta.hasStatus(409);
        risposta.bodyJson().extractingPath("$.title").isEqualTo("Conflitto");
        // Il dettaglio non deve rivelare quale vincolo o valore è andato in conflitto.
        risposta.bodyJson().extractingPath("$.detail").asString().doesNotContain("marta@esempio.it");
    }

    @Test
    void impaginaGliUtentiNellaFormaDelContratto() {
        given(service.listUsers(any()))
                .willReturn(Page.of(List.of(MARTA), 1, PageRequest.firstPage()));

        MvcTestResultAssert risposta = assertThat(mockMvc.get().uri("/api/users"));

        risposta.hasStatusOk();
        risposta.bodyJson().extractingPath("$.items[0].id").isEqualTo(ID.toString());
        risposta.bodyJson().extractingPath("$.total").isEqualTo(1);
        risposta.bodyJson().extractingPath("$.index").isEqualTo(0);
        risposta.bodyJson().extractingPath("$.size").isEqualTo(PageRequest.DEFAULT_SIZE);
        risposta.bodyJson().extractingPath("$.pageCount").isEqualTo(1);
    }

    @Test
    void aggiornaUnUtenteSostituendoProfiloEImpostazioni() {
        User aggiornata = MARTA.withProfile("Luca", "Bianchi", "luca@esempio.it");
        given(service.updateUser(eq(ID), any(), any(), any(), any())).willReturn(aggiornata);

        MvcTestResultAssert risposta = assertThat(mockMvc.put().uri("/api/users/{id}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_VALIDO));

        risposta.hasStatusOk();
        risposta.bodyJson().extractingPath("$.firstName").isEqualTo("Luca");
        risposta.bodyJson().extractingPath("$.lastName").isEqualTo("Bianchi");
    }

    @Test
    void cancellaUnUtenteSenzaCorpo() {
        assertThat(mockMvc.delete().uri("/api/users/{id}", ID)).hasStatus(204);
    }

    @Test
    void ometteICampiAssentiInveceDiSerializzareNull() {
        given(service.getUser(ID)).willReturn(MARTA.withProfile("Marta", null, null));

        MvcTestResultAssert risposta = assertThat(mockMvc.get().uri("/api/users/{id}", ID));

        risposta.hasStatusOk();
        risposta.bodyJson().doesNotHavePath("$.lastName");
        risposta.bodyJson().doesNotHavePath("$.email");
        risposta.bodyJson().extractingPath("$.firstName").isEqualTo("Marta");
    }

    @Test
    void leggeLeImpostazioniInKebabCase() {
        given(service.getSettings(ID)).willReturn(IMPOSTAZIONI);

        MvcTestResultAssert risposta = assertThat(mockMvc.get().uri("/api/users/{id}/settings", ID));

        risposta.hasStatusOk();
        risposta.bodyJson().extractingPath("$.timeZone").isEqualTo("Europe/Rome");
        risposta.bodyJson().extractingPath("$.language").isEqualTo("it");
        risposta.bodyJson().extractingPath("$.defaultDashboardPeriod").isEqualTo("current-month");
    }

    @Test
    void sostituisceLeImpostazioniInBlocco() {
        UserSettings nuove = new UserSettings(UserLanguage.EN, "UTC", DashboardPeriod.LAST_7_DAYS);
        given(service.updateSettings(eq(ID), any())).willReturn(MARTA.withSettings(nuove));

        MvcTestResultAssert risposta = assertThat(mockMvc.put().uri("/api/users/{id}/settings", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"timeZone": "UTC", "language": "en", "defaultDashboardPeriod": "last-7-days"}
                        """));

        risposta.hasStatusOk();
        risposta.bodyJson().extractingPath("$.timeZone").isEqualTo("UTC");
        risposta.bodyJson().extractingPath("$.language").isEqualTo("en");
        risposta.bodyJson().extractingPath("$.defaultDashboardPeriod").isEqualTo("last-7-days");
    }
}
