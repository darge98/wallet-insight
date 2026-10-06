package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.ImportedCategory;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportSource;
import it.walletinsight.core.ingestion.domain.ImportedMovements;
import it.walletinsight.core.movements.domain.Movement;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.AccountDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.CategoryDto;
import it.walletinsight.core.ingestion.infrastructure.budgetbakers.dto.RecordDto;
import it.walletinsight.shared.source.IngestionSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * L'adapter di BudgetBakers: l'unico posto del backend che sa che BudgetBakers esiste.
 *
 * Sta in `infrastructure` perché è un dettaglio su *come* si arriva ai dati, non
 * su cosa siano: il caso d'uso conosce la porta {@link ImportSource} e niente
 * altro. Il token arriva dalla connessione ed è già in chiaro qui — l'ha
 * decifrato il repository — e non lascia mai il processo.
 */
@Component
class BudgetBakersSource implements ImportSource {

    private static final Logger log = LoggerFactory.getLogger(BudgetBakersSource.class);

    /**
     * L'estremo inferiore del primo import: da sempre.
     *
     * Non e' una data scelta e non e' configurabile — e' il modo di dire "senza
     * limite" a un'API che un limite lo pretende. Ometterlo non significa
     * "prendi tutto": BudgetBakers applica allora un filtro suo di tre mesi a
     * ritroso e lo dichiara solo in `appliedRecordDateFilters`, quindi un import
     * scritto senza saperlo sembrerebbe funzionare leggendo un periodo che non
     * ha chiesto.
     *
     * Che sia l'epoch e non una data plausibile e' voluto: qualunque valore piu'
     * recente taglierebbe lo storico, e tagliare lo storico non fa perdere righe
     * vecchie, sbaglia il saldo di oggi — il saldo e' quello iniziale del conto
     * piu' la somma di *tutti* i movimenti. Misurato con tre mesi: PayPal
     * 4531,60 invece di 0, Credem 11987,13 invece di 13220.
     */
    private static final LocalDate DALL_ORIGINE = LocalDate.EPOCH;

    static final Period RILETTURA = Period.ofMonths(3);

    private final BudgetBakersClient client;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final MovementMapper movementMapper;

    BudgetBakersSource(BudgetBakersClient client,
                       AccountMapper accountMapper,
                       CategoryMapper categoryMapper,
                       MovementMapper movementMapper) {
        this.client = client;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
        this.movementMapper = movementMapper;
    }

    @Override
    public IngestionSource source() {
        return IngestionSource.BUDGET_BAKERS;
    }

    @Override
    public List<Account> readAccounts(ImportConnection connection) {
        List<AccountDto> dtos = client.accounts(connection.token().value());
        log.info("BudgetBakers: letti {} conti", dtos.size());
        return dtos.stream()
                .map(dto -> accountMapper.toAccount(dto, connection.userId(), source()))
                .toList();
    }

    @Override
    public List<ImportedCategory> readCategories(ImportConnection connection) {
        List<CategoryDto> dtos = client.categories(connection.token().value());
        log.info("BudgetBakers: lette {} categorie", dtos.size());
        return dtos.stream().map(categoryMapper::toImported).toList();
    }

    /**
     * Legge dal segnaposto, ma mai meno degli ultimi {@link #RILETTURA}.
     *
     * Rileggere tre mesi permette di vedere i movimenti in sospeso confermati
     * giorni dopo (la sorgente crea un record nuovo e toglie il vecchio), quelli
     * registrati in ritardo con una data passata e le correzioni fatte in Wallet.
     * Su un utente reale sono 178 movimenti: una pagina.
     *
     * Il primo giro non ha un punto da cui ripartire e prende tutto
     * ({@link #DALL_ORIGINE}): costa i ~3 secondi misurati su 1678 movimenti,
     * una volta sola nella vita della connessione, ed è la sola finestra che
     * lasci i saldi giusti.
     */
    @Override
    public ImportedMovements readMovements(
            ImportConnection connection, List<Account> accounts,
            Map<String, CategoryId> categoriePerIdSorgente) {
        LocalDate from = connection.neverImported()
                ? DALL_ORIGINE
                : earliest(connection.lastRecordDate(), LocalDate.now().minus(RILETTURA));
        // L'estremo superiore è domani, esclusivo: i movimenti pianificati hanno
        // date future e non sono ancora accaduti.
        LocalDate toExclusive = LocalDate.now().plusDays(1);

        log.info("BudgetBakers: leggo i movimenti dal {} (incluso) al {} (escluso)", from, toExclusive);
        List<RecordDto> records = client.recordsBetween(connection.token().value(), from, toExclusive);
        log.info("BudgetBakers: letti {} record", records.size());

        // Dall'identificativo nella sorgente a quello di Wallet Insights, per i conti e per le
        // categorie: e' l'unico pezzo che l'adapter non puo' sapere da solo, e arriva
        // da cio' che i due moduli hanno appena allineato.
        Map<String, AccountId> contiPerIdSorgente = accounts.stream()
                .collect(Collectors.toMap(Account::externalId, Account::id));

        List<Movement> movements = records.stream()
                .map(dto -> movementMapper.toMovement(dto, connection.userId(), source(),
                        contiPerIdSorgente, categoriePerIdSorgente))
                .filter(Objects::nonNull)
                .toList();
        int orfani = records.size() - movements.size();
        if (orfani > 0) {
            // Gia' loggati uno per uno dal mapper: qui conta il totale, che e' la cosa
            // che si guarda per capire se e' un caso isolato o un import andato storto.
            log.error("BudgetBakers: {} movimenti su {} non hanno un conto fra quelli importati "
                    + "e restano fuori", orfani, records.size());
        }
        return new ImportedMovements(from, toExclusive, movements);
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
