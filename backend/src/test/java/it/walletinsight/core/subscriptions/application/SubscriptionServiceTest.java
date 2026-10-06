package it.walletinsight.core.subscriptions.application;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountKind;
import it.walletinsight.core.accounts.domain.AccountRepository;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.subscriptions.domain.Cadence;
import it.walletinsight.core.subscriptions.domain.CadenceUnit;
import it.walletinsight.core.subscriptions.domain.MonthCharges;
import it.walletinsight.core.subscriptions.domain.SubscriptionDefinition;
import it.walletinsight.core.subscriptions.domain.UpcomingCharge;
import it.walletinsight.core.subscriptions.domain.SubscriptionId;
import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.core.subscriptions.domain.SubscriptionFixtures;
import it.walletinsight.core.subscriptions.domain.SubscriptionRepository;
import it.walletinsight.core.subscriptions.domain.SubscriptionsOverview;
import it.walletinsight.core.users.domain.DashboardPeriod;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserLanguage;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.support.AbstractDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionServiceTest extends AbstractDatabaseTest {

    /** Le 23:30 UTC del 4 ottobre: a Roma è già il 5. */
    private static final Clock SERA_DEL_4_UTC =
            Clock.fixed(Instant.parse("2026-10-04T23:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate CINQUE_OTTOBRE = LocalDate.of(2026, 10, 5);

    @Autowired
    private SubscriptionRepository repository;

    @Autowired
    private UserRepository users;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private AccountRepository accounts;

    private SubscriptionService service;
    private UserId marta;

    @BeforeEach
    void prepara() {
        service = new SubscriptionService(repository, categories, accounts, users, SERA_DEL_4_UTC);
        marta = utente("Marta");
    }

    @Test
    void oggiEQuelloDelFusoDellUtente() {
        assertThat(service.today(marta)).isEqualTo(CINQUE_OTTOBRE);
    }

    @Test
    void gliAddebitiImminentiPartonoDaOggiNelFusoDellUtente() {
        crea("Netflix", Money.of(1_399), Cadence.monthly(), LocalDate.of(2026, 1, 5), null);
        crea("Spotify", Money.of(1_199), Cadence.monthly(), LocalDate.of(2026, 1, 12), null);

        SubscriptionsOverview settimana = service.overview(marta, 7);

        assertThat(settimana.until()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(settimana.upcoming()).extracting(UpcomingCharge::date)
                .containsExactly(CINQUE_OTTOBRE);
    }

    @Test
    void lOrizzonteHaUnLimite() {
        assertThatThrownBy(() -> service.overview(marta, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.overview(marta, 367)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lAbbonamentoDiUnAltroUtenteNonEsiste() {
        Subscription netflix = crea("Netflix", Money.of(1_399), Cadence.monthly(), CINQUE_OTTOBRE, null);
        UserId luca = utente("Luca");

        assertThatThrownBy(() -> aggiorna(luca, netflix.id(), "Mio", Money.of(1),
                Cadence.monthly(), CINQUE_OTTOBRE, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteSubscription(luca, netflix.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void disdireERiattivareCambiaSoloLaFine() {
        Subscription netflix = crea("Netflix", Money.of(1_399), Cadence.monthly(), LocalDate.of(2026, 1, 8), null);

        aggiorna(marta, netflix.id(), "Netflix", Money.of(1_399), Cadence.monthly(),
                LocalDate.of(2026, 1, 8), LocalDate.of(2026, 9, 30));
        assertThat(service.overview(marta, 30).activeCount()).isZero();

        aggiorna(marta, netflix.id(), "Netflix", Money.of(1_399), Cadence.monthly(),
                LocalDate.of(2026, 1, 8), null);
        assertThat(service.overview(marta, 30).activeCount()).isEqualTo(1);
    }

    @Test
    void categoriaEContoDevonoEssereDellUtenteELaCategoriaUnaSottocategoria() {
        Category macro = Category.create(marta, null, "Abbonamenti");
        Category streaming = Category.create(marta, macro.id(), "Streaming");
        categories.insertAll(List.of(macro, streaming));
        Account conto = Account.imported(marta, IngestionSource.BUDGET_BAKERS, "acc-1", "Credem",
                AccountKind.CURRENT_ACCOUNT, CurrencyCode.EUR, Money.zero(), null, false, false);
        accounts.insert(conto);

        Subscription netflix = service.createSubscription(marta, new SubscriptionDefinition("Netflix",
                Money.of(1_399), Cadence.monthly(), CINQUE_OTTOBRE, null, streaming.id(), conto.id()));
        assertThat(service.listSubscriptions(marta)).singleElement().satisfies(letto -> {
            assertThat(letto.categoryId()).isEqualTo(streaming.id());
            assertThat(letto.accountId()).isEqualTo(conto.id());
        });

        assertThatThrownBy(() -> service.updateSubscription(marta, netflix.id(), new SubscriptionDefinition(
                "Netflix", Money.of(1_399), Cadence.monthly(), CINQUE_OTTOBRE, null, macro.id(), null)))
                .isInstanceOf(IllegalArgumentException.class);

        UserId luca = utente("Luca");
        assertThatThrownBy(() -> service.createSubscription(luca, new SubscriptionDefinition(
                "Netflix", Money.of(1_399), Cadence.monthly(), CINQUE_OTTOBRE, null, streaming.id(), null)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.createSubscription(luca, new SubscriptionDefinition(
                "Netflix", Money.of(1_399), Cadence.monthly(), CINQUE_OTTOBRE, null, null, conto.id())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unireUnaCategoriaSpostaAncheGliAbbonamenti() {
        Category macro = Category.create(marta, null, "Abbonamenti");
        Category streaming = Category.create(marta, macro.id(), "Streaming");
        Category tv = Category.create(marta, macro.id(), "TV");
        categories.insertAll(List.of(macro, streaming, tv));
        service.createSubscription(marta, new SubscriptionDefinition("Netflix",
                Money.of(1_399), Cadence.monthly(), CINQUE_OTTOBRE, null, tv.id(), null));

        service.reassignCategory(marta, tv.id(), streaming.id());

        assertThat(service.listSubscriptions(marta)).singleElement()
                .extracting(Subscription::categoryId).isEqualTo(streaming.id());
    }

    @Test
    void ilCalendarioHaGliAddebitiDelMeseEIlTotale() {
        crea("Netflix", Money.of(1_399), Cadence.monthly(), LocalDate.of(2026, 1, 31), null);
        crea("Giornale", Money.of(200), new Cadence(1, CadenceUnit.WEEK), LocalDate.of(2026, 2, 2), null);

        MonthCharges febbraio = service.monthCharges(marta, YearMonth.of(2026, 2));

        assertThat(febbraio.charges()).extracting(UpcomingCharge::date).containsExactly(
                LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 9), LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 23), LocalDate.of(2026, 2, 28));
        assertThat(febbraio.total()).isEqualTo(Money.of(4 * 200 + 1_399));
    }

    private Subscription crea(String nome, Money importo, Cadence cadenza, LocalDate inizio, LocalDate fine) {
        return service.createSubscription(marta, SubscriptionFixtures.definizione(nome, importo, cadenza, inizio, fine));
    }

    private Subscription aggiorna(UserId utente, SubscriptionId id, String nome, Money importo, Cadence cadenza,
                                  LocalDate inizio, LocalDate fine) {
        return service.updateSubscription(utente, id,
                SubscriptionFixtures.definizione(nome, importo, cadenza, inizio, fine));
    }

    private UserId utente(String nome) {
        User user = new User(UserId.newId(), nome, null, null,
                new UserSettings(UserLanguage.IT, "Europe/Rome", DashboardPeriod.CURRENT_MONTH));
        users.insert(user);
        return user.id();
    }
}
