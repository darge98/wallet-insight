package it.walletinsight.core.ingestion.infrastructure.jdbc;

import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcImportConnectionRepositoryTest extends AbstractDatabaseTest {

    private static final PersonalToken TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234");
    private static final PersonalToken ALTRO_TOKEN =
            new PersonalToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.wXyZ9876");

    @Autowired
    private JdbcImportConnectionRepository repository;

    @Autowired
    private UserRepository users;

    private UserId marta;

    @BeforeEach
    void creaUnUtente() {
        marta = nuovoUtente("Marta");
    }

    @Test
    void inserisceERileggeUnaConnessione() {
        ImportConnection connection = collegata(marta, TOKEN);

        repository.insert(connection);

        assertThat(repository.find(marta, IngestionSource.BUDGET_BAKERS)).contains(connection);
    }

    @Test
    void ilSegretoFinisceCifratoNelDatabase() {
        repository.insert(collegata(marta, TOKEN));

        String colonna = jdbc.sql("select encrypted_secret from import_connections")
                .query(String.class)
                .single();

        // Il valore in colonna non assomiglia al token: chi legge il database non lo trova.
        assertThat(colonna).doesNotContain(TOKEN.value()).startsWith("1:");
        assertThat(repository.find(marta, IngestionSource.BUDGET_BAKERS))
                .get().extracting(ImportConnection::token).isEqualTo(TOKEN);
    }

    @Test
    void duePassaggiDiversiSullaStessaSorgenteProduconoCifraturaDiversa() {
        repository.insert(collegata(marta, TOKEN));
        repository.insert(collegata(nuovoUtente("Luca"), TOKEN));

        assertThat(jdbc.sql("select distinct encrypted_secret from import_connections")
                .query(String.class)
                .list())
                .hasSize(2);
    }

    @Test
    void rifiutaLaStessaSorgenteDueVolteperLoStessoUtente() {
        repository.insert(collegata(marta, TOKEN));

        assertThatThrownBy(() -> repository.insert(collegata(marta, ALTRO_TOKEN)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aggiornaCredenzialiStatoETimestampDiModifica() throws InterruptedException {
        ImportConnection connection = collegata(marta, TOKEN);
        repository.insert(connection);
        // now() di PostgreSQL è il tempo della transazione: senza attesa created_at e
        // updated_at coinciderebbero anche se l'update funziona.
        Thread.sleep(10);

        ImportConnection ricollegata =
                connection.reconfigure(ALTRO_TOKEN, false, LocalDate.now().plusDays(1));
        repository.update(ricollegata);

        assertThat(repository.find(marta, IngestionSource.BUDGET_BAKERS)).contains(ricollegata);
        assertThat(timestamp("created_at")).isBefore(timestamp("updated_at"));
    }

    @Test
    void scollegaUnaSorgenteESoloUnaVolta() {
        repository.insert(collegata(marta, TOKEN));

        assertThat(repository.delete(marta, IngestionSource.BUDGET_BAKERS)).isTrue();
        assertThat(repository.delete(marta, IngestionSource.BUDGET_BAKERS)).isFalse();
        assertThat(repository.findByUser(marta)).isEmpty();
    }

    @Test
    void cancellareLUtenteNeCancellaLeCredenziali() {
        repository.insert(collegata(marta, TOKEN));

        users.deleteById(marta);

        assertThat(jdbc.sql("select count(*) from import_connections").query(Long.class).single())
                .isZero();
    }

    @Test
    void findByUserVedeSoloLeConnessioniDellUtente() {
        UserId luca = nuovoUtente("Luca");
        repository.insert(collegata(marta, TOKEN));
        repository.insert(collegata(luca, ALTRO_TOKEN));

        assertThat(repository.findByUser(marta)).singleElement()
                .extracting(ImportConnection::userId).isEqualTo(marta);
    }

    @Test
    void findEnabledByUserIgnoraLeConnessioniInPausa() {
        UserId luca = nuovoUtente("Luca");
        repository.insert(collegata(marta, TOKEN));
        repository.insert(ImportConnection.configure(
                luca, IngestionSource.BUDGET_BAKERS, ALTRO_TOKEN, false, LocalDate.now()));

        assertThat(repository.findEnabledByUser(marta)).singleElement()
                .extracting(ImportConnection::userId).isEqualTo(marta);
        // In pausa: l'utente ha fermato l'import senza scollegare la sorgente.
        assertThat(repository.findEnabledByUser(luca)).isEmpty();
    }

    @Test
    void leConnessioniDaImportarePerUnaSorgenteSonoQuelleAttive() {
        UserId luca = nuovoUtente("Luca");
        UserId giulia = nuovoUtente("Giulia");
        nuovoUtente("Senza sorgenti");
        repository.insert(collegata(marta, TOKEN));
        repository.insert(collegata(giulia, ALTRO_TOKEN));
        repository.insert(ImportConnection.configure(
                luca, IngestionSource.BUDGET_BAKERS, ALTRO_TOKEN, false, LocalDate.now()));

        assertThat(repository.findEnabledBySource(IngestionSource.BUDGET_BAKERS))
                .extracting(ImportConnection::userId).containsExactly(marta, giulia);
        assertThat(repository.findEnabledBySource(IngestionSource.PSD2)).isEmpty();
    }

    @Test
    void ilSegnapostoNasceVuotoEVieneScrittoDaSolo() throws InterruptedException {
        ImportConnection connection = collegata(marta, TOKEN);
        repository.insert(connection);
        assertThat(repository.find(marta, IngestionSource.BUDGET_BAKERS))
                .get().extracting(ImportConnection::lastRecordDate).isNull();
        Thread.sleep(10);

        LocalDate ultimo = LocalDate.now().minusDays(2);
        repository.updateLastRecordDate(connection.importedThrough(ultimo));

        assertThat(repository.find(marta, IngestionSource.BUDGET_BAKERS))
                .get().extracting(ImportConnection::lastRecordDate).isEqualTo(ultimo);
        assertThat(timestamp("created_at")).isBefore(timestamp("updated_at"));
    }

    @Test
    void scrivereIlSegnapostoNonToccaLeCredenziali() {
        ImportConnection connection = collegata(marta, TOKEN);
        repository.insert(connection);
        String cifratoPrima = jdbc.sql("select encrypted_secret from import_connections")
                .query(String.class).single();

        repository.updateLastRecordDate(connection.importedThrough(LocalDate.now()));

        // Il segreto non viene ricifrato: la colonna e' identica, byte per byte.
        assertThat(jdbc.sql("select encrypted_secret from import_connections")
                .query(String.class).single()).isEqualTo(cifratoPrima);
    }

    @Test
    void unaSorgenteFuoriContrattoNelDatabaseEUnDatoCorrotto() {
        // Simula una riga scritta da un percorso sconosciuto: il mapping deve segnalare
        // dati corrotti (500), non una richiesta malformata (400).
        jdbc.sql("""
                insert into import_connections (id, user_id, source, encrypted_secret, configured_at)
                values (:id, :userId, 'carta-perforata', '1:qualcosa', current_date)
                """)
                .param("id", UUID.randomUUID())
                .param("userId", marta.value())
                .update();

        assertThatThrownBy(() -> repository.findByUser(marta))
                .isInstanceOf(CorruptedDataException.class)
                .hasMessageContaining("source");
    }

    private UserId nuovoUtente(String firstName) {
        User user = new User(UserId.newId(), firstName, null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        return user.id();
    }

    private static ImportConnection collegata(UserId userId, PersonalToken token) {
        return ImportConnection.configure(
                userId, IngestionSource.BUDGET_BAKERS, token, true, LocalDate.now());
    }

    private OffsetDateTime timestamp(String colonna) {
        return jdbc.sql("select %s from import_connections".formatted(colonna))
                .query(OffsetDateTime.class)
                .single();
    }
}
