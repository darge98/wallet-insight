package it.walletinsight.bff.onboarding;

import it.walletinsight.bff.onboarding.OnboardingService.OnboardingResult;
import it.walletinsight.core.ingestion.application.ImportConnectionService;
import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionId;
import it.walletinsight.core.ingestion.domain.ImportOutcome;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Cosa succede *dopo* le due scritture: chi collega una sorgente porta dentro
 * anche i suoi dati, subito. Le scritture stanno in {@link OnboardingRegistration}
 * e qui sono un doppio — quello che si verifica è l'orchestrazione.
 */
class OnboardingServiceTest {

    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);
    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");
    private static final User MARTA =
            new User(UserId.newId(), "Marta", null, null, IMPOSTAZIONI);
    private static final ImportConnection APPENA_COLLEGATA = new ImportConnection(
            ImportConnectionId.newId(), MARTA.id(), IngestionSource.BUDGET_BAKERS,
            TOKEN, true, LocalDate.of(2026, 9, 24));
    /** La stessa connessione dopo l'import: segnaposto ed esecuzione valorizzati. */
    private static final ImportConnection IMPORTATA = APPENA_COLLEGATA
            .importedThrough(LocalDate.of(2026, 9, 23))
            .ranAt(Instant.parse("2026-09-24T08:00:00Z"));

    private final OnboardingRegistration registration = mock(OnboardingRegistration.class);
    private final ImportConnectionService connections = mock(ImportConnectionService.class);
    private final ImportService imports = mock(ImportService.class);
    private final OnboardingService service =
            new OnboardingService(registration, connections, imports);

    @Test
    void chiCollegaUnaSorgenteNeImportaSubitoIDati() {
        given(registration.register(any(), any(), any(), any(), any(), any()))
                .willReturn(new OnboardingResult(MARTA, List.of(APPENA_COLLEGATA)));
        given(imports.importFor(MARTA.id())).willReturn(List.of(
                ImportOutcome.succeeded(IngestionSource.BUDGET_BAKERS, 6, 95, 412, 412,
                        LocalDate.of(2026, 9, 23))));
        given(connections.listConnections(MARTA.id())).willReturn(List.of(IMPORTATA));

        OnboardingResult result = service.complete(
                "Marta", null, null, IMPOSTAZIONI, IngestionSource.BUDGET_BAKERS, TOKEN);

        assertThat(result.user()).isEqualTo(MARTA);
        // La connessione che torna è quella riletta *dopo* l'import: quella di prima
        // direbbe "mai importata" di una sorgente da cui i dati sono già dentro.
        assertThat(result.connections()).containsExactly(IMPORTATA);
        InOrder ordine = inOrder(registration, imports, connections);
        ordine.verify(registration).register(
                "Marta", null, null, IMPOSTAZIONI, IngestionSource.BUDGET_BAKERS, TOKEN);
        ordine.verify(imports).importFor(MARTA.id());
        ordine.verify(connections).listConnections(MARTA.id());
    }

    @Test
    void chiRimandaLaSceltaNonFaPartireNessunImport() {
        OnboardingResult registrato = new OnboardingResult(MARTA, List.of());
        given(registration.register(any(), any(), any(), any(), any(), any()))
                .willReturn(registrato);

        OnboardingResult result =
                service.complete("Marta", null, null, IMPOSTAZIONI, null, null);

        assertThat(result).isEqualTo(registrato);
        // Nessuna sorgente da leggere: chiedere l'import sarebbe un giro a vuoto, e
        // rileggere le connessioni una query per sapere che non ce ne sono.
        verifyNoInteractions(imports, connections);
    }

    @Test
    void unImportChePerdeNonFaPerdereIlPrimoAccesso() {
        given(registration.register(any(), any(), any(), any(), any(), any()))
                .willReturn(new OnboardingResult(MARTA, List.of(APPENA_COLLEGATA)));
        given(imports.importFor(MARTA.id())).willReturn(List.of(
                ImportOutcome.failed(IngestionSource.BUDGET_BAKERS, "401 dalla sorgente")));
        given(connections.listConnections(MARTA.id())).willReturn(List.of(APPENA_COLLEGATA));

        OnboardingResult result = service.complete(
                "Marta", null, null, IMPOSTAZIONI, IngestionSource.BUDGET_BAKERS, TOKEN);

        // Il profilo esiste e la sorgente è collegata: i dati arriveranno col giro
        // successivo. Rifiutare tutto costringerebbe a rifare un'iscrizione valida.
        assertThat(result.user()).isEqualTo(MARTA);
        assertThat(result.connections()).containsExactly(APPENA_COLLEGATA);
    }

}
