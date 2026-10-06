package it.walletinsight.core.ingestion.application;

import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportConnectionRepository;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.page.PageRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test dell'orchestrazione, legati alle porte e non a JDBC: entrambi i repository
 * sono fake in-memory. Qui si verifica l'idempotenza del collegamento, che è la
 * regola su cui poggia il riprovare dell'onboarding.
 */
class ImportConnectionServiceTest {

    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");
    private static final PersonalToken ALTRO_TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.wXyZ9876");

    private final FakeImportConnectionRepository repository = new FakeImportConnectionRepository();
    private final FakeUserRepository users = new FakeUserRepository();
    private final ImportConnectionService service = new ImportConnectionService(repository, users);

    private final UserId marta = users.aggiungiUtente("Marta");

    @Test
    void collegaUnaSorgenteAllUtente() {
        ImportConnection connection =
                service.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, true);

        assertThat(connection.userId()).isEqualTo(marta);
        assertThat(connection.configuredAt()).isEqualTo(LocalDate.now());
        assertThat(service.listConnections(marta)).containsExactly(connection);
    }

    @Test
    void ricollegareSostituisceIlTokenSenzaDuplicareLaConnessione() {
        ImportConnection prima = service.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, true);

        ImportConnection dopo =
                service.configure(marta, IngestionSource.BUDGET_BAKERS, ALTRO_TOKEN, true);

        assertThat(dopo.id()).isEqualTo(prima.id());
        assertThat(dopo.token()).isEqualTo(ALTRO_TOKEN);
        assertThat(service.listConnections(marta)).containsExactly(dopo);
    }

    @Test
    void utentiDiversiHannoConnessioniDiverseSullaStessaSorgente() {
        UserId luca = users.aggiungiUtente("Luca");

        service.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, true);
        service.configure(luca, IngestionSource.BUDGET_BAKERS, ALTRO_TOKEN, true);

        assertThat(service.listConnections(marta)).singleElement()
                .extracting(ImportConnection::token).isEqualTo(TOKEN);
        assertThat(service.listConnections(luca)).singleElement()
                .extracting(ImportConnection::token).isEqualTo(ALTRO_TOKEN);
    }

    @Test
    void unUtenteSconosciutoNonPuoCollegareNulla() {
        UserId ignoto = UserId.newId();

        assertThatThrownBy(() -> service.configure(ignoto, IngestionSource.BUDGET_BAKERS, TOKEN, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Utente");
        assertThatThrownBy(() -> service.listConnections(ignoto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void registraFinDoveLImportEArrivato() {
        service.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, true);
        LocalDate ultimo = LocalDate.now().minusDays(3);

        ImportConnection aggiornata =
                service.recordImported(marta, IngestionSource.BUDGET_BAKERS, ultimo);

        assertThat(aggiornata.lastRecordDate()).isEqualTo(ultimo);
        assertThat(service.listConnections(marta)).containsExactly(aggiornata);
    }

    @Test
    void unaDataPiuVecchiaNonArretraIlSegnaposto() {
        service.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, true);
        LocalDate ultimo = LocalDate.now();
        service.recordImported(marta, IngestionSource.BUDGET_BAKERS, ultimo);

        assertThat(service.recordImported(marta, IngestionSource.BUDGET_BAKERS, ultimo.minusDays(30))
                .lastRecordDate()).isEqualTo(ultimo);
    }

    @Test
    void nonSiRegistraUnImportSuUnaSorgenteMaiCollegata() {
        assertThatThrownBy(() ->
                service.recordImported(marta, IngestionSource.BUDGET_BAKERS, LocalDate.now()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Connessione");
    }

    @Test
    void scollegaUnaSorgenteSoloSeEraCollegata() {
        service.configure(marta, IngestionSource.BUDGET_BAKERS, TOKEN, true);

        service.disconnect(marta, IngestionSource.BUDGET_BAKERS);

        assertThat(service.listConnections(marta)).isEmpty();
        assertThatThrownBy(() -> service.disconnect(marta, IngestionSource.BUDGET_BAKERS))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Connessione");
    }

    /** Riproduce l'unicità della coppia (utente, sorgente) garantita dal database. */
    private static final class FakeImportConnectionRepository implements ImportConnectionRepository {

        private final Map<String, ImportConnection> byKey = new LinkedHashMap<>();

        @Override
        public List<ImportConnection> findByUser(UserId userId) {
            return byKey.values().stream().filter(c -> c.userId().equals(userId)).toList();
        }

        @Override
        public Optional<ImportConnection> find(UserId userId, IngestionSource source) {
            return Optional.ofNullable(byKey.get(key(userId, source)));
        }

        @Override
        public void insert(ImportConnection connection) {
            byKey.put(key(connection.userId(), connection.source()), connection);
        }

        @Override
        public List<ImportConnection> findEnabledByUser(UserId userId) {
            return byKey.values().stream()
                    .filter(c -> c.userId().equals(userId) && c.enabled())
                    .toList();
        }

        @Override
        public List<ImportConnection> findEnabledBySource(IngestionSource source) {
            return byKey.values().stream()
                    .filter(c -> c.source() == source && c.enabled())
                    .toList();
        }

        @Override
        public void update(ImportConnection connection) {
            byKey.put(key(connection.userId(), connection.source()), connection);
        }

        @Override
        public void updateLastRecordDate(ImportConnection connection) {
            byKey.put(key(connection.userId(), connection.source()), connection);
        }

        @Override
        public void updateLastRunAt(ImportConnection connection) {
            byKey.put(key(connection.userId(), connection.source()), connection);
        }

        @Override
        public void updateCredentialsRejectedAt(ImportConnection connection) {
            byKey.put(key(connection.userId(), connection.source()), connection);
        }

        @Override
        public boolean delete(UserId userId, IngestionSource source) {
            return byKey.remove(key(userId, source)) != null;
        }

        private static String key(UserId userId, IngestionSource source) {
            return userId + ":" + source;
        }
    }

    private static final class FakeUserRepository implements UserRepository {

        private final Map<UserId, User> byId = new LinkedHashMap<>();

        UserId aggiungiUtente(String firstName) {
            User user = new User(UserId.newId(), firstName, null, null,
                    new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
            byId.put(user.id(), user);
            return user.id();
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<User> findAll(PageRequest request) {
            return List.copyOf(byId.values());
        }

        @Override
        public long count() {
            return byId.size();
        }

        @Override
        public void insert(User user) {
            byId.put(user.id(), user);
        }

        @Override
        public void update(User user) {
            byId.put(user.id(), user);
        }

        @Override
        public boolean deleteById(UserId id) {
            return byId.remove(id) != null;
        }
    }
}
