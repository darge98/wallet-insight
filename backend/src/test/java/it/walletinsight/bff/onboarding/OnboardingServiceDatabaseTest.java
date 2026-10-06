package it.walletinsight.bff.onboarding;

import it.walletinsight.bff.onboarding.OnboardingService.OnboardingResult;
import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * La promessa che il frontend non poteva farsi da solo: due scritture in due moduli,
 * una transazione sola. Con due chiamate HTTP separate un token rifiutato lasciava
 * dietro di sé un utente creato a metà.
 *
 * L'import è un doppio: qui non interessa cosa legga — ha i suoi test — ma
 * *quando* venga chiesto, cioè a transazione già chiusa.
 */
class OnboardingServiceDatabaseTest extends AbstractDatabaseTest {

    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);
    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");

    @Autowired
    private OnboardingService service;

    @MockitoBean
    private ImportService imports;

    @Test
    void salvaProfiloESorgenteInsieme() {
        OnboardingResult result = service.complete(
                "Marta", "Rossi", "marta@esempio.it", IMPOSTAZIONI,
                IngestionSource.BUDGET_BAKERS, TOKEN);

        assertThat(conta("users")).isOne();
        assertThat(conta("import_connections")).isOne();
        assertThat(result.connections()).singleElement()
                .satisfies(connection -> assertThat(connection.userId()).isEqualTo(result.user().id()));
        // Anche passando dall'onboarding il segreto arriva cifrato: la cifratura sta nel
        // repository, non nel percorso che lo attraversa.
        assertThat(jdbc.sql("select encrypted_secret from import_connections")
                .query(String.class)
                .single())
                .doesNotContain(TOKEN.value());
    }

    /**
     * L'import parte, e parte fuori dalla transazione.
     *
     * Il conteggio dentro il doppio è la misura: senza una transazione impegnata
     * quella lettura, che gira su una connessione sua, vedrebbe zero righe. È il
     * motivo per cui le due scritture stanno in un bean a parte — un metodo
     * `@Transactional` chiamato dall'interno non aprirebbe nessun confine.
     */
    @Test
    void lImportParteAScrittureGiaCommesse() {
        given(imports.importFor(any())).willAnswer(invocazione -> {
            assertThat(conta("users")).isOne();
            assertThat(conta("import_connections")).isOne();
            return List.of();
        });

        OnboardingResult result = service.complete(
                "Marta", null, null, IMPOSTAZIONI, IngestionSource.BUDGET_BAKERS, TOKEN);

        verify(imports).importFor(result.user().id());
    }

    @Test
    void unaSorgenteRifiutataNonLasciaUnUtenteAMeta() {
        assertThatThrownBy(() -> service.complete(
                "Marta", null, null, IMPOSTAZIONI, IngestionSource.PSD2, TOKEN))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(conta("users")).isZero();
        assertThat(conta("import_connections")).isZero();
        // Niente da leggere: la sorgente non è mai stata collegata.
        verifyNoInteractions(imports);
    }

    @Test
    void chiRimandaLaSceltaOttieneSoloIlProfilo() {
        service.complete("Marta", null, null, IMPOSTAZIONI, null, null);

        assertThat(conta("users")).isOne();
        assertThat(conta("import_connections")).isZero();
        verifyNoInteractions(imports);
    }

    private long conta(String tabella) {
        return jdbc.sql("select count(*) from %s".formatted(tabella)).query(Long.class).single();
    }
}
