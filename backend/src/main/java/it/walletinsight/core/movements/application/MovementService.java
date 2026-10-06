package it.walletinsight.core.movements.application;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.Category;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.CategoryRepository;
import it.walletinsight.core.movements.domain.CategorySpending;
import it.walletinsight.core.movements.domain.ConvertedMovements;
import it.walletinsight.core.movements.domain.CounterPartySpending;
import it.walletinsight.core.movements.domain.CumulativeExpensePoint;
import it.walletinsight.core.movements.domain.ImportedWindow;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.movements.domain.MovementFilter;
import it.walletinsight.core.movements.domain.MovementId;
import it.walletinsight.core.movements.domain.MovementRepository;
import it.walletinsight.core.movements.domain.MovementSort;
import it.walletinsight.core.movements.domain.MovementTotals;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import it.walletinsight.shared.source.IngestionSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Casi d'uso dei movimenti: portarli dentro, rileggerli, correggerli.
 *
 * {@link #saveImported} è idempotente perché lo è la scrittura sotto, non perché
 * qui si controlli qualcosa: il riconoscimento è il vincolo
 * `(user_id, source, external_id)` nel database. Ed è la proprietà che regge
 * tutto l'import incrementale — ogni giro rilegge di proposito il giorno già
 * importato, quindi ripresentare movimenti già visti è la norma, non l'eccezione.
 *
 * {@link #updateMovement} è la novità rispetto a prima, quando un movimento non
 * si poteva toccare. Non contraddice la ragione di allora: un movimento resta il
 * racconto di un fatto avvenuto altrove, e infatti importo, data, verso e stato
 * non si correggono nemmeno adesso. Quello che si può cambiare sono le tre cose
 * che la sorgente non sa dire bene — com'è descritto, chi c'è dall'altra parte,
 * in che categoria sta — e ognuna vive accanto al valore della sorgente, senza
 * cancellarlo: il prossimo import non ha niente da riscoprire, e il dato con cui
 * il movimento è arrivato resta leggibile anche dopo che è stato riscritto.
 */
@Service
@Transactional
public class MovementService {

    private static final String USER_RESOURCE_TYPE = "Utente";
    private static final String MOVEMENT_RESOURCE_TYPE = "Movimento";
    private static final String CATEGORY_RESOURCE_TYPE = "Categoria";

    private final MovementRepository repository;
    private final CategoryRepository categories;
    private final UserRepository users;

    public MovementService(MovementRepository repository,
                           CategoryRepository categories,
                           UserRepository users) {
        this.repository = repository;
        this.categories = categories;
        this.users = users;
    }

    /**
     * Salva i movimenti letti da una sorgente e dice quanti hanno cambiato qualcosa.
     *
     * Il numero restituito non è quanti ne sono arrivati: è quanti sono stati
     * creati o riallineati davvero. Rigirare lo stesso import deve dare zero, ed è
     * il modo in cui l'utente vede la differenza fra "non c'era niente di nuovo" e
     * "non ha funzionato".
     */
    public int saveImported(List<Movement> movements) {
        return repository.upsertAll(movements);
    }

    /**
     * Salva una finestra letta per intero dalla sorgente e toglie i movimenti in
     * sospeso che non ci sono più, in una transazione sola: il movimento confermato
     * e quello in sospeso che sostituisce non devono mai contare insieme.
     *
     * @param accounts i conti che la sorgente ha restituito in questo giro: solo su
     *                 quelli l'assenza di un movimento vuol dire che è stato tolto
     */
    public ImportedWindow saveImportedWindow(UserId userId, IngestionSource source,
                                             LocalDate from, LocalDate toExclusive,
                                             Set<AccountId> accounts, List<Movement> movements) {
        int salvati = repository.upsertAll(movements);
        List<String> letti = movements.stream().map(Movement::externalId).toList();
        List<Movement> rimossi = repository.deleteMissingUncleared(
                userId, source, from, toExclusive, accounts, letti);
        return new ImportedWindow(salvati, rimossi);
    }

    /**
     * Completa la classificazione dei movimenti che hanno la traccia della
     * sorgente ma non ancora il riferimento risolto, e dice quanti ne ha sistemati.
     *
     * La chiama l'import subito dopo aver allineato le categorie, e serve perché
     * un import è incrementale: senza, uno storico già importato resterebbe senza
     * classificazione fino al giorno in cui la sorgente ne toccasse le righe.
     * Niente di già agganciato viene riscritto, riclassificazioni comprese.
     */
    public int linkCategories(UserId userId, IngestionSource source) {
        return repository.linkCategoriesFromSource(userId, source);
    }

    @Transactional(readOnly = true)
    public Page<Movement> listMovements(
            UserId userId, MovementFilter filter, MovementSort sort, PageRequest page) {
        return repository.findPage(userId, zoneOf(userId), filter, sort, page);
    }

    /**
     * Quanto pesa l'intero risultato di un filtro: entrate, uscite, netto, quanti.
     *
     * Sta accanto all'elenco e non dentro, pur rispondendo alla stessa domanda,
     * perché chi mostra solo una riga o un conteggio non deve pagare anche
     * l'aggregazione. A chiederli insieme è il BFF, che sa cosa serve alla schermata.
     */
    @Transactional(readOnly = true)
    public MovementTotals totals(UserId userId, MovementFilter filter) {
        return repository.totals(userId, zoneOf(userId), filter);
    }

    /** Le categorie su cui è uscito di più fra i movimenti del filtro: identificatori e importi. */
    @Transactional(readOnly = true)
    public List<CategorySpending> expensesByCategory(
            UserId userId, MovementFilter filter, int limit) {
        return repository.expensesByCategory(userId, zoneOf(userId), filter, limit);
    }

    /** Le controparti verso cui è uscito di più fra i movimenti del filtro. */
    @Transactional(readOnly = true)
    public List<CounterPartySpending> topCounterParties(
            UserId userId, MovementFilter filter, int limit) {
        return repository.topCounterParties(userId, zoneOf(userId), filter, limit);
    }

    /** La spesa cumulata giorno per giorno nel periodo: il ritmo con cui si consuma. */
    @Transactional(readOnly = true)
    public List<CumulativeExpensePoint> cumulativeExpenses(UserId userId, DateRange period) {
        return repository.cumulativeExpenses(userId, zoneOf(userId), period);
    }

    /**
     * Cambia le tre cose di un movimento che si correggono.
     *
     * Tutti e tre i parametri seguono la stessa regola, ed è la più semplice che
     * ci sia: {@code null} significa *non toccare*, qualsiasi altro valore
     * sostituisce. Una stringa vuota è un valore, e lascia il campo vuoto: non
     * c'è più nessuna seconda colonna pronta a riprendersi la scena, che è tutta
     * la ragione per cui V11 ha unito i campi.
     *
     * Una categoria che non è dell'utente è un 404 e non un 403: rispondere
     * "non hai il permesso" confermerebbe che esiste.
     *
     * Un movimento ancora da contabilizzare è un 409: non c'è niente da correggere
     * nella richiesta, è il momento a essere sbagliato — vedi {@link Movement#editable()}.
     *
     * Chiamarlo senza cambiare niente non scrive: `updated_at` non è un registro
     * di quante volte è stata aperta la schermata.
     */
    public Movement updateMovement(
            UserId userId,
            MovementId movementId,
            String description,
            String counterParty,
            CategoryId category) {
        requireUser(userId);
        Movement movimento = repository.findById(userId, movementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        MOVEMENT_RESOURCE_TYPE, movementId.toString()));

        if (!movimento.editable()) {
            throw new IllegalStateException(
                    "Questo movimento è ancora da contabilizzare e non si può correggere: "
                            + "quando la banca lo conferma la sorgente lo sostituisce con un "
                            + "altro, e quello che ci scrivi adesso andrebbe perso.");
        }

        Movement aggiornato = movimento;
        if (description != null) {
            aggiornato = aggiornato.describedAs(description);
        }
        if (counterParty != null) {
            aggiornato = aggiornato.attributedTo(counterParty);
        }
        if (category != null) {
            Category scelta = categories.findById(userId, category).orElseThrow(
                    () -> new ResourceNotFoundException(CATEGORY_RESOURCE_TYPE, category.toString()));
            if (scelta.isMacro()) {
                throw new IllegalArgumentException(
                        "«%s» è una macro: scegli una delle sue sottocategorie.".formatted(scelta.name()));
            }
            aggiornato = aggiornato.classifiedAs(category);
        }

        if (aggiornato.equals(movimento)) {
            return movimento;
        }
        repository.updateEditable(aggiornato);
        return aggiornato;
    }

    /**
     * Quanto i movimenti hanno spostato su ciascun conto, in centesimi.
     *
     * Non è il saldo: è la parte di saldo che viene dai movimenti. Il saldo
     * corrente è questo più il saldo iniziale del conto, e a comporre i due pezzi
     * è il BFF, che è l'unico posto che vede entrambi i moduli.
     */
    public int reassignCategory(UserId userId, CategoryId from, CategoryId to) {
        return repository.reassignCategory(userId, from, to);
    }

    public int reassignFromSource(UserId userId, IngestionSource source, String sourceCategoryExternalId,
                                  CategoryId from, CategoryId to) {
        return repository.reassignFromSource(userId, source, sourceCategoryExternalId, from, to);
    }

    @Transactional(readOnly = true)
    public Map<AccountId, Long> movedByAccount(UserId userId) {
        requireUser(userId);
        return repository.sumByAccount(userId);
    }

    /** Come {@link #movedByAccount}, in euro: la base del patrimonio fra conti di valute diverse. */
    @Transactional(readOnly = true)
    public Map<AccountId, ConvertedMovements> convertedByAccount(UserId userId) {
        requireUser(userId);
        return repository.convertedByAccount(userId);
    }

    @Transactional(readOnly = true)
    public long countImported(UserId userId, IngestionSource source) {
        requireUser(userId);
        return repository.countByUserAndSource(userId, source);
    }

    private void requireUser(UserId userId) {
        zoneOf(userId);
    }

    /** Il fuso del profilo: decide in quale giorno cade ogni movimento. */
    private ZoneId zoneOf(UserId userId) {
        return users.findById(userId)
                .map(utente -> ZoneId.of(utente.settings().timeZone()))
                .orElseThrow(() -> new ResourceNotFoundException(USER_RESOURCE_TYPE, userId.toString()));
    }
}
