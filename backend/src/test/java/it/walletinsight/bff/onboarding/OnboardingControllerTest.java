package it.walletinsight.bff.onboarding;

import it.walletinsight.bff.onboarding.OnboardingService.OnboardingResult;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionId;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResultAssert;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Contratto HTTP della chiamata unica di primo accesso. */
@WebMvcTest(OnboardingController.class)
class OnboardingControllerTest {

    private static final UserId ID =
            UserId.of(UUID.fromString("019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f"));
    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);
    private static final User MARTA = new User(ID, "Marta", "Rossi", "marta@esempio.it", IMPOSTAZIONI);
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234";
    private static final ImportConnection CONNESSIONE = new ImportConnection(
            ImportConnectionId.newId(), ID, IngestionSource.BUDGET_BAKERS,
            new PersonalToken(TOKEN), true, LocalDate.of(2026, 9, 21));

    private static final String PROFILO = """
            "profile": {
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
    private OnboardingService service;

    @Test
    void creaProfiloESorgenteInUnaChiamataSola() {
        given(service.complete(any(), any(), any(), any(), any(), any()))
                .willReturn(new OnboardingResult(MARTA, List.of(CONNESSIONE)));

        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          %s,
                          "importConnection": {"source": "budget-bakers", "token": "%s"}
                        }
                        """.formatted(PROFILO, TOKEN)));

        // La risorsa creata è l'utente: l'onboarding è il gesto, non una cosa che resta.
        risposta.hasStatus(201).hasHeader("Location", "/api/users/" + ID);
        risposta.bodyJson().extractingPath("$.user.id").isEqualTo(ID.toString());
        risposta.bodyJson().extractingPath("$.user.defaultDashboardPeriod").isEqualTo("current-month");
        risposta.bodyJson().extractingPath("$.connections[0].source").isEqualTo("budget-bakers");
        risposta.bodyJson().extractingPath("$.connections[0].secretHint").isEqualTo("…1234");
        verify(service).complete("Marta", "Rossi", "marta@esempio.it", IMPOSTAZIONI,
                IngestionSource.BUDGET_BAKERS, new PersonalToken(TOKEN));
    }

    @Test
    void ilTokenNonTornaMaiNellaRisposta() {
        given(service.complete(any(), any(), any(), any(), any(), any()))
                .willReturn(new OnboardingResult(MARTA, List.of(CONNESSIONE)));

        assertThat(mockMvc.post().uri("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {%s, "importConnection": {"source": "budget-bakers", "token": "%s"}}
                        """.formatted(PROFILO, TOKEN)))
                .hasStatus(201)
                .bodyText().doesNotContain(TOKEN);
    }

    @Test
    void rimandareLaSceltaDellaSorgenteEAmmesso() {
        given(service.complete(any(), any(), any(), any(), isNull(), isNull()))
                .willReturn(new OnboardingResult(MARTA, List.of()));

        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{%s}".formatted(PROFILO)));

        risposta.hasStatus(201);
        risposta.bodyJson().extractingPath("$.connections").asArray().isEmpty();
    }

    @Test
    void rifiutaUnOnboardingSenzaProfilo() {
        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.errors[0].field").isEqualTo("profile");
        verifyNoInteractions(service);
    }

    @Test
    void rifiutaUnProfiloIncompletoSenzaCreareNulla() {
        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"profile": {"timeZone": "Europe/Rome", "language": "it",
                         "defaultDashboardPeriod": "current-month"}}
                        """));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.errors[0].field").isEqualTo("profile.firstName");
        verifyNoInteractions(service);
    }

    @Test
    void rifiutaUnTokenCheNonEUnJwt() {
        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {%s, "importConnection": {"source": "budget-bakers", "token": "a-meta"}}
                        """.formatted(PROFILO)));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.detail").isEqualTo("Il token personale non è un JWT valido.");
        verifyNoInteractions(service);
    }

    @Test
    void rifiutaUnaSorgenteSconosciuta() {
        MvcTestResultAssert risposta = assertThat(mockMvc.post().uri("/api/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {%s, "importConnection": {"source": "carta-perforata", "token": "%s"}}
                        """.formatted(PROFILO, TOKEN)));

        risposta.hasStatus(400);
        risposta.bodyJson().extractingPath("$.title").isEqualTo("Richiesta non valida");
        verifyNoInteractions(service);
    }
}
