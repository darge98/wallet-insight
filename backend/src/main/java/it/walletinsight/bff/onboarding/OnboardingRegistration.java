package it.walletinsight.bff.onboarding;

import it.walletinsight.bff.onboarding.OnboardingService.OnboardingResult;
import it.walletinsight.core.categories.application.CategoryService;
import it.walletinsight.core.ingestion.application.ImportConnectionService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.application.UserService;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.source.IngestionSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Le due scritture del primo accesso, in una transazione sola.
 *
 * È un collaboratore separato da {@link OnboardingService} e non un metodo suo
 * per una ragione tecnica precisa: il primo accesso ora fa anche partire un
 * import, che dura secondi e parla con una sorgente esterna, e quello **non**
 * deve stare dentro la transazione. Chiamare un proprio metodo `@Transactional`
 * dall'interno non aprirebbe nulla — il proxy di Spring si scavalca da sé — e il
 * confine diventerebbe una bugia scritta in un'annotazione. Due bean, invece,
 * fanno quello che dicono: qui dentro si commette, e solo dopo si esce a parlare
 * con la rete.
 *
 * L'ordine — il profilo prima, la sorgente dopo — resta quello di sempre: una
 * connessione ha bisogno di un utente che esista già.
 */
@Service
@Transactional
class OnboardingRegistration {

    private final UserService users;
    private final ImportConnectionService imports;
    private final CategoryService categories;

    OnboardingRegistration(UserService users, ImportConnectionService imports, CategoryService categories) {
        this.users = users;
        this.imports = imports;
        this.categories = categories;
    }

    /** {@code source} e {@code token} sono nulli quando l'utente rimanda la scelta. */
    OnboardingResult register(
            String firstName,
            String lastName,
            String email,
            UserSettings settings,
            IngestionSource source,
            PersonalToken token) {
        User user = users.createUser(firstName, lastName, email, settings);
        // L'elenco di base subito, anche senza sorgente: è ciò che l'import usa per
        // classificare, e ciò su cui si costruiscono i budget.
        categories.ensureDefaults(user.id());

        if (source == null) {
            return new OnboardingResult(user, List.of());
        }

        ImportConnection connection = imports.configure(user.id(), source, token, true);
        return new OnboardingResult(user, List.of(connection));
    }
}
