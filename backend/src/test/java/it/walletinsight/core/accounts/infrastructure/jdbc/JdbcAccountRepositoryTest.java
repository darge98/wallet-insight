package it.walletinsight.core.accounts.infrastructure.jdbc;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.CorruptedDataException;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcAccountRepositoryTest extends AbstractDatabaseTest {

    @Autowired
    private JdbcAccountRepository repository;

    @Autowired
    private UserRepository users;

    private UserId marta;

    @BeforeEach
    void creaUnUtente() {
        marta = nuovoUtente("Marta");
    }

    @Test
    void inserisceERileggeUnConto() {
        Account conto = importato(marta, "acc-1", "Credem");

        repository.insert(conto);

        assertThat(repository.findByExternalId(marta, IngestionSource.BUDGET_BAKERS, "acc-1"))
                .contains(conto);
    }

    @Test
    void ilSaldoInizialeSoprevviveInCentesimiInteri() {
        repository.insert(importato(marta, "acc-1", "Credem"));

        assertThat(jdbc.sql("select initial_balance_cents from accounts").query(Long.class).single())
                .isEqualTo(892_108L);
        assertThat(repository.findByUser(marta).getFirst().initialBalance())
                .isEqualTo(Money.of(892_108L, CurrencyCode.EUR));
    }

    @Test
    void leEnumVannoNelDatabaseInKebabCase() {
        repository.insert(importato(marta, "acc-1", "Credem"));

        assertThat(jdbc.sql("select kind from accounts").query(String.class).single())
                .isEqualTo("current-account");
        assertThat(jdbc.sql("select source from accounts").query(String.class).single())
                .isEqualTo("budget-bakers");
    }

    @Test
    void loStessoContoDellaSorgenteNonPuoEntrareDueVolte() {
        repository.insert(importato(marta, "acc-1", "Credem"));

        assertThatThrownBy(() -> repository.insert(importato(marta, "acc-1", "Credem")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void utentiDiversiPossonoAvereLoStessoContoDellaSorgente() {
        UserId luca = nuovoUtente("Luca");

        repository.insert(importato(marta, "acc-1", "Credem"));
        repository.insert(importato(luca, "acc-1", "Credem"));

        assertThat(repository.findByUser(marta)).hasSize(1);
        assertThat(repository.findByUser(luca)).hasSize(1);
    }

    @Test
    void aggiornaIlNomeDellaSorgenteSenzaToccareIlRiferimento() {
        Account conto = importato(marta, "acc-1", "Credem");
        repository.insert(conto);

        Account rinominato = conto.renamedTo("Conto stipendio")
                .refreshedFrom(importato(marta, "acc-1", "Credem Banca"));
        repository.update(rinominato);

        Account riletto = repository.findByUser(marta).getFirst();
        assertThat(riletto.name()).isEqualTo("Conto stipendio");
        assertThat(riletto.sourceName()).isEqualTo("Credem Banca");
        assertThat(riletto.externalId()).isEqualTo("acc-1");
        assertThat(riletto.id()).isEqualTo(conto.id());
    }

    @Test
    void cancellareLUtenteNeCancellaIConti() {
        repository.insert(importato(marta, "acc-1", "Credem"));

        users.deleteById(marta);

        assertThat(jdbc.sql("select count(*) from accounts").query(Long.class).single()).isZero();
    }

    @Test
    void unTipoFuoriContrattoNelDatabaseEUnDatoCorrotto() {
        jdbc.sql("""
                insert into accounts (id, user_id, source, external_id, name, source_name, kind,
                                      currency_code, initial_balance_cents)
                values (:id, :userId, 'budget-bakers', 'acc-x', 'Strano', 'Strano', 'salvadanaio',
                        'EUR', 0)
                """)
                .param("id", UUID.randomUUID())
                .param("userId", marta.value())
                .update();

        assertThatThrownBy(() -> repository.findByUser(marta))
                .isInstanceOf(CorruptedDataException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void ilContoDiUnAltroUtenteNonSiTrovaPerIdentificatore() {
        UserId luca = nuovoUtente("Luca");
        Account diMarta = importato(marta, "acc-1", "Credem");
        repository.insert(diMarta);

        assertThat(repository.findById(marta, diMarta.id())).contains(diMarta);
        // L'utente e' parte della domanda, non un controllo aggiunto dopo: per Luca
        // quel conto non esiste, e non c'e' un ramo da ricordarsi di scrivere.
        assertThat(repository.findById(luca, diMarta.id())).isEmpty();
    }

    private UserId nuovoUtente(String firstName) {
        User user = new User(UserId.newId(), firstName, null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        return user.id();
    }

    private static Account importato(UserId userId, String externalId, String nome) {
        return Account.imported(userId, IngestionSource.BUDGET_BAKERS, externalId, nome,
                AccountKind.CURRENT_ACCOUNT, CurrencyCode.EUR, Money.of(892_108L),
                "F010000712861", false, false);
    }
}
