package it.walletinsight.core.users.infrastructure.jdbc;

import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.shared.page.PageRequest;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUserRepositoryTest extends AbstractDatabaseTest {

    private static final UserSettings IMPOSTAZIONI =
            new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH);

    @Autowired
    private JdbcUserRepository repository;

    private static User nuovoUtente(String firstName, String email) {
        return new User(UserId.newId(), firstName, null, email, IMPOSTAZIONI);
    }

    @Test
    void inserisceERileggeUnUtenteCompleto() {
        User user = new User(
                UserId.newId(), "Marta", "Rossi", "marta@esempio.it",
                new UserSettings(UserLanguage.EN, "UTC", DashboardPeriod.LAST_30_DAYS));

        repository.insert(user);

        assertThat(repository.findById(user.id())).contains(user);
    }

    @Test
    void ammettePiuUtentiSenzaEmail() {
        repository.insert(nuovoUtente("Prima", null));
        repository.insert(nuovoUtente("Seconda", null));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void rifiutaDueUtentiConLaStessaEmail() {
        repository.insert(nuovoUtente("Prima", "dup@esempio.it"));

        assertThatThrownBy(() -> repository.insert(nuovoUtente("Seconda", "dup@esempio.it")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aggiornaProfiloImpostazioniETimestampDiModifica() throws InterruptedException {
        User user = nuovoUtente("Marta", null);
        repository.insert(user);
        // now() di PostgreSQL è il tempo della transazione: senza attese created_at e
        // updated_at coinciderebbero anche se l'update funziona.
        Thread.sleep(10);

        User aggiornata = user.withProfile("Marta", "Rossi", "marta@esempio.it")
                .withSettings(new UserSettings(UserLanguage.EN, "Europe/Rome", DashboardPeriod.TODAY));
        repository.update(aggiornata);

        assertThat(repository.findById(user.id())).contains(aggiornata);
        assertThat(leggeTimestamp("created_at", user.id()))
                .isBefore(leggeTimestamp("updated_at", user.id()));
    }

    @Test
    void cancellaUnUtenteESoloUnaVolta() {
        User user = nuovoUtente("Marta", null);
        repository.insert(user);

        assertThat(repository.deleteById(user.id())).isTrue();
        assertThat(repository.findById(user.id())).isEmpty();
        assertThat(repository.deleteById(user.id())).isFalse();
    }

    @Test
    void findAllImpaginaInOrdineDiIdentificatore() {
        for (int i = 0; i < 5; i++) {
            repository.insert(nuovoUtente("Utente " + i, null));
        }

        List<User> primaPagina = repository.findAll(PageRequest.of(0, 2));
        List<User> secondaPagina = repository.findAll(PageRequest.of(1, 2));
        List<User> terzaPagina = repository.findAll(PageRequest.of(2, 2));

        assertThat(primaPagina).hasSize(2);
        assertThat(secondaPagina).hasSize(2);
        assertThat(terzaPagina).hasSize(1);
        // Con gli UUIDv7 l'ordine per ID è cronologico: le pagine non si sovrappongono.
        assertThat(List.of(
                primaPagina.get(0), primaPagina.get(1),
                secondaPagina.get(0), secondaPagina.get(1),
                terzaPagina.get(0)))
                .isSortedAccordingTo((a, b) -> a.id().value().compareTo(b.id().value()));
        assertThat(repository.count()).isEqualTo(5);
    }

    @Test
    void unaEnumFuoriContrattoNelDatabaseEUnDatoCorrotto() {
        // Simula una riga scritta da un percorso sconosciuto: il mapping deve segnalare
        // dati corrotti (500), non una richiesta malformata (400).
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into users (id, first_name, time_zone, language, default_dashboard_period)
                values (:id, 'Corrotta', 'UTC', 'xx', 'today')
                """)
                .param("id", id)
                .update();

        assertThatThrownBy(() -> repository.findById(UserId.of(id)))
                .isInstanceOf(CorruptedDataException.class)
                .hasMessageContaining("language");
    }

    private OffsetDateTime leggeTimestamp(String colonna, UserId id) {
        return jdbc.sql("select %s from users where id = :id".formatted(colonna))
                .param("id", id.value())
                .query(OffsetDateTime.class)
                .single();
    }
}
