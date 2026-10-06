package it.walletinsight.core.users.application;

import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test dell'orchestrazione, legati alla porta e non a JDBC: il repository è un fake
 * in-memory che riproduce l'ordinamento per ID dell'adapter reale.
 */
class UserServiceTest {

    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);

    private final FakeUserRepository repository = new FakeUserRepository();
    private final UserService service = new UserService(repository);

    @Test
    void createUserAssegnaUnIdentificatoreSequenziale() {
        User user = service.createUser("Marta", "Rossi", " marta@esempio.it ", IMPOSTAZIONI);

        assertThat(user.id().value().version()).isEqualTo(7);
        assertThat(user.email()).isEqualTo("marta@esempio.it");
        assertThat(repository.findById(user.id())).contains(user);
    }

    @Test
    void getUserAssenteSollevaNonTrovato() {
        UserId id = UserId.newId();

        assertThatThrownBy(() -> service.getUser(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Utente")
                .hasMessageContaining(id.toString());
    }

    @Test
    void listUsersRestituisceLaPaginaCoiTotali() {
        service.createUser("A", null, null, IMPOSTAZIONI);
        service.createUser("B", null, null, IMPOSTAZIONI);
        service.createUser("C", null, null, IMPOSTAZIONI);

        Page<User> page = service.listUsers(PageRequest.of(1, 2));

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.index()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.pageCount()).isEqualTo(2);
    }

    @Test
    void listUsersOrdinaPerIdentificatore() {
        for (int i = 0; i < 5; i++) {
            service.createUser("Utente " + i, null, null, IMPOSTAZIONI);
        }

        List<User> users = service.listUsers(PageRequest.of(0, 10)).items();

        assertThat(users).isSortedAccordingTo(Comparator.comparing(user -> user.id().value()));
    }

    @Test
    void updateUserSostituisceProfiloEImpostazioni() {
        User user = service.createUser("Marta", null, null, IMPOSTAZIONI);
        UserSettings nuove = new UserSettings(UserLanguage.EN, "UTC", DashboardPeriod.LAST_7_DAYS);

        User aggiornata = service.updateUser(user.id(), "Luca", "Bianchi", "luca@esempio.it", nuove);

        assertThat(service.getUser(user.id())).isEqualTo(aggiornata);
        assertThat(aggiornata.firstName()).isEqualTo("Luca");
        assertThat(aggiornata.settings()).isEqualTo(nuove);
    }

    @Test
    void updateUserAssenteSollevaNonTrovato() {
        assertThatThrownBy(() -> service.updateUser(UserId.newId(), "X", null, null, IMPOSTAZIONI))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteUserLoRimuoveDavvero() {
        User user = service.createUser("Marta", null, null, IMPOSTAZIONI);

        service.deleteUser(user.id());

        assertThat(repository.findById(user.id())).isEmpty();
    }

    @Test
    void deleteUserAssenteSollevaNonTrovato() {
        assertThatThrownBy(() -> service.deleteUser(UserId.newId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getSettingsLeggeLeImpostazioniCorrenti() {
        User user = service.createUser("Marta", null, null, IMPOSTAZIONI);

        assertThat(service.getSettings(user.id())).isEqualTo(IMPOSTAZIONI);
    }

    @Test
    void updateSettingsSostituisceLeImpostazioniInBlocco() {
        User user = service.createUser("Marta", null, null, IMPOSTAZIONI);
        UserSettings nuove = new UserSettings(UserLanguage.EN, "UTC", DashboardPeriod.TODAY);

        User aggiornata = service.updateSettings(user.id(), nuove);

        assertThat(aggiornata.settings()).isEqualTo(nuove);
        assertThat(aggiornata.firstName()).isEqualTo("Marta");
        assertThat(service.getSettings(user.id())).isEqualTo(nuove);
    }

    private static final class FakeUserRepository implements UserRepository {

        private final Map<UserId, User> users = new LinkedHashMap<>();

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public List<User> findAll(PageRequest request) {
            return users.values().stream()
                    .sorted(Comparator.comparing(user -> user.id().value()))
                    .skip(request.offset())
                    .limit(request.size())
                    .toList();
        }

        @Override
        public long count() {
            return users.size();
        }

        @Override
        public void insert(User user) {
            users.put(user.id(), user);
        }

        @Override
        public void update(User user) {
            users.put(user.id(), user);
        }

        @Override
        public boolean deleteById(UserId id) {
            return users.remove(id) != null;
        }
    }
}
