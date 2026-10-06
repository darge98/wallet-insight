package it.walletinsight.core.accounts.application;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.accounts.domain.AccountRepository;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.page.PageRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qui si verifica l'idempotenza dell'allineamento: è la regola su cui poggia il
 * poter rigirare un import senza duplicare i conti né perderne gli identificatori.
 */
class AccountServiceTest {

    private final FakeAccountRepository repository = new FakeAccountRepository();
    private final FakeUserRepository users = new FakeUserRepository();
    private final AccountService service = new AccountService(repository, users);

    private final UserId marta = users.aggiungiUtente("Marta");

    @Test
    void creaIContiCheNonEsistono() {
        List<Account> risultato = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"), conto("acc-2", "Contanti")));

        assertThat(risultato).hasSize(2);
        assertThat(service.listAccounts(marta)).hasSize(2);
        assertThat(repository.inserimenti).isEqualTo(2);
    }

    @Test
    void rigirareLoStessoImportNonDuplicaNullaENonCambiaGliIdentificatori() {
        List<Account> primo = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"), conto("acc-2", "Contanti")));

        List<Account> secondo = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"), conto("acc-2", "Contanti")));

        assertThat(service.listAccounts(marta)).hasSize(2);
        assertThat(secondo).extracting(Account::id)
                .containsExactlyElementsOf(primo.stream().map(Account::id).toList());
        // Niente e' cambiato: nessuna scrittura inutile, updated_at resta fermo.
        assertThat(repository.aggiornamenti).isZero();
    }

    @Test
    void unaRinominaDellUtenteSopravviveAllImportSuccessivo() {
        Account creato = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))).getFirst();
        repository.salva(creato.renamedTo("Conto stipendio"));

        List<Account> dopo = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem Banca")));

        assertThat(dopo).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("Conto stipendio");
            assertThat(c.sourceName()).isEqualTo("Credem Banca");
            assertThat(c.id()).isEqualTo(creato.id());
        });
    }

    @Test
    void unContoSparitoDallaSorgenteNonVieneCancellato() {
        service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"), conto("acc-2", "Contanti")));

        service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS, List.of(conto("acc-1", "Credem")));

        // Possiede movimenti storici: che oggi la sorgente non lo elenchi non basta a distruggerlo.
        assertThat(service.listAccounts(marta)).hasSize(2);
    }

    @Test
    void unContoArchiviatoEntraComunque() {
        Account archiviato = Account.imported(marta, IngestionSource.BUDGET_BAKERS, "acc-9",
                "Vecchio conto", AccountKind.CURRENT_ACCOUNT, CurrencyCode.EUR,
                Money.of(0L), null, true, false);

        assertThat(service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS, List.of(archiviato)))
                .singleElement()
                .extracting(Account::archived).isEqualTo(true);
    }

    @Test
    void unUtenteSconosciutoNonHaConti() {
        UserId ignoto = UserId.newId();

        assertThatThrownBy(() -> service.syncFromSource(ignoto, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Utente");
    }

    @Test
    void rinominareCambiaSoloIlNomeDiMargine() {
        Account creato = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))).getFirst();

        Account rinominato = service.updateAppearance(marta, creato.id(), "Conto stipendio", null);

        assertThat(rinominato.name()).isEqualTo("Conto stipendio");
        assertThat(rinominato.sourceName()).isEqualTo("Credem");
        assertThat(rinominato.renamedByUser()).isTrue();
    }

    @Test
    void rinominarloComeLoChiamaLaSorgenteLoRimetteASeguirla() {
        Account creato = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))).getFirst();
        service.updateAppearance(marta, creato.id(), "Conto stipendio", null);

        // Tornare al nome della sorgente e' il modo per annullare la rinomina: da qui
        // in avanti il conto ricomincia a seguire come lo chiamano la'.
        service.updateAppearance(marta, creato.id(), "Credem", null);

        assertThat(service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem Banca"))))
                .singleElement()
                .extracting(Account::name).isEqualTo("Credem Banca");
    }

    @Test
    void rinominareConLoStessoNomeNonScrive() {
        Account creato = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))).getFirst();

        service.updateAppearance(marta, creato.id(), "Credem", null);

        // `updated_at` non e' un registro di quante volte si e' aperta la schermata.
        assertThat(repository.aggiornamenti).isZero();
    }

    @Test
    void nonSiRinominaIlContoDiUnAltroUtente() {
        UserId luca = users.aggiungiUtente("Luca");
        Account diMarta = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))).getFirst();

        // Per Luca quel conto non esiste: e' la stessa risposta che darebbe un id inventato,
        // e non rivela che appartiene a qualcun altro.
        assertThatThrownBy(() -> service.updateAppearance(luca, diMarta.id(), "Mio ora", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Conto");
        assertThat(service.listAccounts(marta)).singleElement()
                .extracting(Account::name).isEqualTo("Credem");
    }

    @Test
    void nonSiRinominaUnContoCheNonEsiste() {
        assertThatThrownBy(() -> service.updateAppearance(marta, AccountId.newId(), "Qualsiasi", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Conto");
    }

    @Test
    void cambiaNomeEColoreInUnaScritturaSola() {
        Account creato = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))).getFirst();

        Account aggiornato =
                service.updateAppearance(marta, creato.id(), "Conto stipendio", "#f97316");

        assertThat(aggiornato.name()).isEqualTo("Conto stipendio");
        assertThat(aggiornato.color()).isEqualTo("#f97316");
        // Una sola: sono la stessa domanda, "come voglio vedere questo conto".
        assertThat(repository.aggiornamenti).isEqualTo(1);
    }

    @Test
    void unCampoAssenteVuolDireNonToccare() {
        Account creato = service.syncFromSource(marta, IngestionSource.BUDGET_BAKERS,
                List.of(conto("acc-1", "Credem"))).getFirst();
        service.updateAppearance(marta, creato.id(), "Conto stipendio", "#f97316");

        Account soloColore = service.updateAppearance(marta, creato.id(), null, "#0ea5e9");

        assertThat(soloColore.color()).isEqualTo("#0ea5e9");
        assertThat(soloColore.name()).isEqualTo("Conto stipendio");
    }

    private Account conto(String externalId, String nome) {
        return Account.imported(marta, IngestionSource.BUDGET_BAKERS, externalId, nome,
                AccountKind.CURRENT_ACCOUNT, CurrencyCode.EUR, Money.of(892_108L), null, false, false);
    }

    /** Riproduce l'unicita' della terna (utente, sorgente, id esterno) garantita dal database. */
    private static final class FakeAccountRepository implements AccountRepository {

        private final Map<String, Account> byKey = new LinkedHashMap<>();
        private int inserimenti;
        private int aggiornamenti;

        void salva(Account account) {
            byKey.put(key(account.userId(), account.source(), account.externalId()), account);
        }

        @Override
        public List<Account> findByUser(UserId userId) {
            return new ArrayList<>(byKey.values().stream().filter(a -> a.userId().equals(userId)).toList());
        }

        @Override
        public List<Account> findByUserAndSource(UserId userId, IngestionSource source) {
            return byKey.values().stream()
                    .filter(a -> a.userId().equals(userId) && a.source() == source).toList();
        }

        @Override
        public Optional<Account> findByExternalId(UserId userId, IngestionSource source, String externalId) {
            return Optional.ofNullable(byKey.get(key(userId, source, externalId)));
        }

        @Override
        public Optional<Account> findById(UserId userId, AccountId id) {
            return byKey.values().stream()
                    .filter(a -> a.userId().equals(userId) && a.id().equals(id))
                    .findFirst();
        }

        @Override
        public void insert(Account account) {
            inserimenti++;
            salva(account);
        }

        @Override
        public void update(Account account) {
            aggiornamenti++;
            salva(account);
        }

        private static String key(UserId userId, IngestionSource source, String externalId) {
            return userId + ":" + source + ":" + externalId;
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
