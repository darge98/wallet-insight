package it.walletinsight.core.subscriptions.application;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.accounts.domain.AccountRepository;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.subscriptions.domain.MonthCharges;
import it.walletinsight.core.subscriptions.domain.Subscription;
import it.walletinsight.core.subscriptions.domain.SubscriptionDefinition;
import it.walletinsight.core.subscriptions.domain.SubscriptionId;
import it.walletinsight.core.subscriptions.domain.SubscriptionRepository;
import it.walletinsight.core.subscriptions.domain.SubscriptionsOverview;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.platform.web.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Casi d'uso degli abbonamenti.
 *
 * «Oggi» è quello del fuso dell'utente: decide quale addebito è il prossimo e se
 * un abbonamento disdetto è già finito.
 */
@Service
@Transactional
public class SubscriptionService {

    public static final int MAX_HORIZON_DAYS = 366;

    private static final String USER_RESOURCE_TYPE = "Utente";
    private static final String SUBSCRIPTION_RESOURCE_TYPE = "Abbonamento";
    private static final String CATEGORY_RESOURCE_TYPE = "Categoria";
    private static final String ACCOUNT_RESOURCE_TYPE = "Conto";

    private final SubscriptionRepository repository;
    private final CategoryRepository categories;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final Clock clock;

    @Autowired
    public SubscriptionService(SubscriptionRepository repository,
                               CategoryRepository categories,
                               AccountRepository accounts,
                               UserRepository users) {
        this(repository, categories, accounts, users, Clock.systemUTC());
    }

    SubscriptionService(SubscriptionRepository repository,
                        CategoryRepository categories,
                        AccountRepository accounts,
                        UserRepository users,
                        Clock clock) {
        this.repository = repository;
        this.categories = categories;
        this.accounts = accounts;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Subscription> listSubscriptions(UserId userId) {
        requireUser(userId);
        return repository.findByUser(userId);
    }

    @Transactional(readOnly = true)
    public LocalDate today(UserId userId) {
        return today(requireUser(userId));
    }

    /** Gli addebiti dei prossimi {@code days} giorni, oggi compreso. */
    @Transactional(readOnly = true)
    public SubscriptionsOverview overview(UserId userId, int days) {
        if (days < 1 || days > MAX_HORIZON_DAYS) {
            throw new IllegalArgumentException(
                    "L'orizzonte va da 1 a %d giorni.".formatted(MAX_HORIZON_DAYS));
        }
        LocalDate oggi = today(requireUser(userId));
        return SubscriptionsOverview.of(repository.findByUser(userId), oggi, oggi.plusDays(days - 1L));
    }

    /** Gli addebiti di un mese di calendario, passato o futuro. */
    @Transactional(readOnly = true)
    public MonthCharges monthCharges(UserId userId, YearMonth month) {
        requireUser(userId);
        return MonthCharges.of(repository.findByUser(userId), month);
    }

    public Subscription createSubscription(UserId userId, SubscriptionDefinition definition) {
        requireUser(userId);
        requireOwnReferences(userId, definition);
        Subscription nuovo = Subscription.create(userId, definition);
        repository.insert(nuovo);
        return nuovo;
    }

    /** Riscrive l'intera definizione: una data di fine assente vuol dire «non disdetto». */
    public Subscription updateSubscription(UserId userId, SubscriptionId id, SubscriptionDefinition definition) {
        Subscription esistente = requireSubscription(userId, id);
        requireOwnReferences(userId, definition);
        Subscription aggiornato = esistente.redefinedAs(definition);
        repository.update(aggiornato);
        return aggiornato;
    }

    public void deleteSubscription(UserId userId, SubscriptionId id) {
        requireSubscription(userId, id);
        repository.delete(userId, id);
    }

    /** Quando una categoria viene unita in un'altra, i suoi abbonamenti la seguono. */
    public void reassignCategory(UserId userId, CategoryId from, CategoryId into) {
        repository.reassignCategory(userId, from, into);
    }

    /**
     * Categoria e conto devono essere dell'utente, e la categoria una sottocategoria:
     * è lì che stanno i movimenti. Uno di un altro utente è un 404, non un 403: dire
     * «vietato» confermerebbe che esiste.
     */
    private void requireOwnReferences(UserId userId, SubscriptionDefinition definition) {
        CategoryId categoriaId = definition.categoryId();
        if (categoriaId != null) {
            Category categoria = categories.findById(userId, categoriaId).orElseThrow(
                    () -> new ResourceNotFoundException(CATEGORY_RESOURCE_TYPE, categoriaId.toString()));
            if (categoria.isMacro()) {
                throw new IllegalArgumentException(
                        "«%s» è una macro: scegli una delle sue sottocategorie.".formatted(categoria.name()));
            }
        }
        AccountId contoId = definition.accountId();
        if (contoId != null && accounts.findById(userId, contoId).isEmpty()) {
            throw new ResourceNotFoundException(ACCOUNT_RESOURCE_TYPE, contoId.toString());
        }
    }

    private Subscription requireSubscription(UserId userId, SubscriptionId id) {
        requireUser(userId);
        return repository.findById(userId, id).orElseThrow(
                () -> new ResourceNotFoundException(SUBSCRIPTION_RESOURCE_TYPE, id.toString()));
    }

    private LocalDate today(User user) {
        return LocalDate.now(clock.withZone(ZoneId.of(user.settings().timeZone())));
    }

    private User requireUser(UserId userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_RESOURCE_TYPE, userId.toString()));
    }
}
