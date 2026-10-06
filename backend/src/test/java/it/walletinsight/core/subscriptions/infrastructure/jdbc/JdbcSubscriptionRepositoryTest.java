package it.walletinsight.core.subscriptions.infrastructure.jdbc;

import it.walletinsight.core.subscriptions.domain.Cadence;
import it.walletinsight.core.subscriptions.domain.CadenceUnit;
import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.core.subscriptions.domain.SubscriptionFixtures;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.money.Money;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSubscriptionRepositoryTest extends AbstractDatabaseTest {

    @Autowired
    private JdbcSubscriptionRepository repository;

    @Autowired
    private UserRepository users;

    private UserId marta;

    @BeforeEach
    void preparaUtente() {
        marta = utente("Marta");
    }

    @Test
    void rileggeCadenzaEDate() {
        Subscription assicurazione = SubscriptionFixtures.abbonamento(marta, "Assicurazione", Money.of(12_000),
                new Cadence(3, CadenceUnit.MONTH), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 12, 31));
        repository.insert(assicurazione);

        assertThat(repository.findByUser(marta)).containsExactly(assicurazione);
    }

    @Test
    void riscriveLaDefinizioneETogliereLaFineLaToglieDavvero() {
        Subscription netflix = SubscriptionFixtures.abbonamento(marta, "Netflix", Money.of(1_399), Cadence.monthly(),
                LocalDate.of(2026, 1, 8), LocalDate.of(2026, 12, 8));
        repository.insert(netflix);

        Subscription riattivato = netflix.redefinedAs(SubscriptionFixtures.definizione("Netflix Premium",
                Money.of(1_999), new Cadence(1, CadenceUnit.YEAR), LocalDate.of(2026, 2, 1), null));
        repository.update(riattivato);

        assertThat(repository.findById(marta, netflix.id())).contains(riattivato);
    }

    @Test
    void nonVedeGliAbbonamentiDiUnAltroUtente() {
        Subscription netflix = SubscriptionFixtures.abbonamento(marta, "Netflix", Money.of(1_399), Cadence.monthly(),
                LocalDate.of(2026, 1, 8), null);
        repository.insert(netflix);
        UserId luca = utente("Luca");

        assertThat(repository.findById(luca, netflix.id())).isEmpty();
        repository.delete(luca, netflix.id());
        assertThat(repository.findByUser(marta)).hasSize(1);
    }

    @Test
    void loSchemaRifiutaUnaFinePrimaDelPrimoAddebito() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into subscriptions (id, user_id, name, amount_cents, cadence_every, cadence_unit, start_date, end_date)
                values (gen_random_uuid(), :userId, 'Netflix', 1399, 1, 'month', date '2026-10-01', date '2026-09-01')
                """).param("userId", marta.value()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserId utente(String nome) {
        User user = new User(UserId.newId(), nome, null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        return user.id();
    }
}
