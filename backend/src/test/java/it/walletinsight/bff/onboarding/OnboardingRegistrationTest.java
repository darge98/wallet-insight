package it.walletinsight.bff.onboarding;

import it.walletinsight.bff.onboarding.OnboardingService.OnboardingResult;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.ingestion.application.ImportConnectionService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionId;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.application.UserService;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.source.IngestionSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * La composizione dei due domini. I servizi di modulo sono mockkati: qui non si
 * verifica cosa fanno, ma che le due scritture avvengano nell'ordine giusto — una
 * connessione ha bisogno di un utente che esista già.
 */
class OnboardingRegistrationTest {

    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);
    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");
    private static final User MARTA =
            new User(UserId.newId(), "Marta", null, null, IMPOSTAZIONI);

    private final UserService users = mock(UserService.class);
    private final ImportConnectionService imports = mock(ImportConnectionService.class);
    private final CategoryService categories = mock(CategoryService.class);
    private final OnboardingRegistration registration =
            new OnboardingRegistration(users, imports, categories);

    @Test
    void creaIlProfiloEPoiCollegaLaSorgente() {
        ImportConnection connessione = new ImportConnection(
                ImportConnectionId.newId(), MARTA.id(), IngestionSource.BUDGET_BAKERS,
                TOKEN, true, LocalDate.now());
        given(users.createUser(any(), any(), any(), any())).willReturn(MARTA);
        given(imports.configure(MARTA.id(), IngestionSource.BUDGET_BAKERS, TOKEN, true))
                .willReturn(connessione);

        OnboardingResult result = registration.register(
                "Marta", null, null, IMPOSTAZIONI, IngestionSource.BUDGET_BAKERS, TOKEN);

        assertThat(result.user()).isEqualTo(MARTA);
        assertThat(result.connections()).containsExactly(connessione);
        // L'ordine non è un dettaglio: la connessione è appesa a un utente che deve esistere.
        InOrder ordine = inOrder(users, imports);
        ordine.verify(users).createUser("Marta", null, null, IMPOSTAZIONI);
        ordine.verify(imports).configure(MARTA.id(), IngestionSource.BUDGET_BAKERS, TOKEN, true);
    }

    @Test
    void chiRimandaLaSceltaOttieneSoloIlProfilo() {
        given(users.createUser(any(), any(), any(), any())).willReturn(MARTA);

        OnboardingResult result =
                registration.register("Marta", null, null, IMPOSTAZIONI, null, null);

        assertThat(result.user()).isEqualTo(MARTA);
        assertThat(result.connections()).isEmpty();
        verifyNoInteractions(imports);
    }

}
