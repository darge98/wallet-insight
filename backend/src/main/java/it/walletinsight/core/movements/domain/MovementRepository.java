package it.walletinsight.core.movements.domain;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.daterange.DateRange;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import it.walletinsight.shared.source.IngestionSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Porta di persistenza dei movimenti.
 *
 * Le domande per periodo portano il fuso di chi le fa: i movimenti sono istanti, e
 * quale giorno sia un istante lo decide il calendario dell'utente.
 *
 * Diversa da quella dei conti in un punto: per l'import non c'è un `insert`
 * accanto a un `update`, c'è {@link #upsertAll}. I conti si rileggono prima di
 * scrivere perché hanno campi dell'utente da non calpestare; i movimenti ora ne
 * hanno anche loro, ma la regola che li protegge è dentro la stessa istruzione
 * SQL invece che in una lettura per riga — con qualche migliaio di righe per
 * import quelle letture sarebbero migliaia di viaggi al database per scoprire
 * quasi sempre che non è cambiato niente.
 *
 * Le modifiche dell'utente passano invece da {@link #updateEditable}, che è un
 * percorso separato apposta: scrive **solo** le tre colonne che gli
 * appartengono, e non può quindi sovrascrivere per sbaglio un fatto della
 * sorgente.
 */
public interface MovementRepository {

    /**
     * Scrive i movimenti letti da una sorgente: crea quelli nuovi, riallinea quelli noti.
     *
     * Idempotente per il vincolo `(user_id, source, external_id)`, non per un
     * controllo nel codice. Restituisce quante righe hanno davvero cambiato
     * qualcosa: rigirare lo stesso import deve dare zero, ed è la misura con cui
     * lo si verifica.
     *
     * Descrizione, pagatore/pagante e riclassificazione dell'utente non vengono
     * toccati: l'istruzione non li nomina.
     */
    int upsertAll(List<Movement> movements);

    /**
     * Cancella i movimenti in sospeso che la sorgente non restituisce più e li restituisce.
     *
     * Solo quelli nella finestra {@code [from, toExclusive)}, sui conti indicati e
     * con un identificativo esterno assente da {@code keptExternalIds}: quando la
     * banca conferma, la sorgente crea un record nuovo e fa sparire quello in sospeso.
     */
    List<Movement> deleteMissingUncleared(UserId userId, IngestionSource source,
                                          LocalDate from, LocalDate toExclusive,
                                          Collection<AccountId> accounts,
                                          Collection<String> keptExternalIds);

    /**
     * Aggancia alla loro categoria i movimenti che ne portano la traccia ma non il
     * riferimento risolto, e dice quanti ne ha riagganciati.
     *
     * Esiste perché il riferimento risolto può mancare per due motivi diversi, e
     * nessuno dei due è un errore: la categoria è stata creata nella sorgente dopo
     * il movimento che ci sta dentro, oppure — misurato — la colonna è appena nata
     * con una migrazione su uno storico già importato. In entrambi i casi la
     * traccia della sorgente basta a ricostruire l'aggancio senza chiedere niente
     * a nessuno.
     *
     * È idempotente e si spegne da sola: quando non c'è più niente da riagganciare
     * non tocca nessuna riga, e l'indice parziale la rende gratuita in quel caso —
     * che è quello normale.
     */
    int linkCategoriesFromSource(UserId userId, IngestionSource source);

    /** Sposta in {@code to} tutti i movimenti classificati in {@code from}. */
    int reassignCategory(UserId userId, CategoryId from, CategoryId to);

    /**
     * Sposta in {@code to} i movimenti arrivati da una categoria della sorgente che
     * sono ancora in {@code from}, cioè dove l'aggancio precedente li aveva messi.
     */
    int reassignFromSource(UserId userId, IngestionSource source, String sourceCategoryExternalId,
                           CategoryId from, CategoryId to);

    /** Una pagina di movimenti, nell'ordine richiesto. */
    Page<Movement> findPage(
            UserId userId, ZoneId zone, MovementFilter filter, MovementSort sort, PageRequest page);

    /**
     * Quanto pesa l'intero risultato di un filtro, al di là della pagina mostrata.
     *
     * È una seconda interrogazione e non un calcolo sulla pagina, perché il
     * totale di venticinque righe non risponde alla domanda che si fa guardando
     * un elenco filtrato. Le due condividono gli stessi criteri.
     */
    MovementTotals totals(UserId userId, ZoneId zone, MovementFilter filter);

    /**
     * Le categorie su cui è uscito di più fra i movimenti del filtro, dalla maggiore.
     *
     * Restituisce identificatori e importi: il nome delle categorie lo mette il
     * BFF, che è l'unico posto che vede anche quel modulo. Il filtro è lo stesso
     * dell'elenco, così una classifica accanto a un elenco ristretto a un conto
     * parla di quel conto.
     */
    List<CategorySpending> expensesByCategory(
            UserId userId, ZoneId zone, MovementFilter filter, int limit);

    /** Le controparti verso cui è uscito di più fra i movimenti del filtro, dalla maggiore. */
    List<CounterPartySpending> topCounterParties(
            UserId userId, ZoneId zone, MovementFilter filter, int limit);

    /**
     * La spesa cumulata giorno per giorno nel periodo, in ordine di data.
     *
     * Il progressivo lo calcola il database con una finestra: farlo qui vorrebbe
     * dire portarsi in memoria una riga per giorno per sommarle in un ciclo, e la
     * stessa query serve già a raggrupparle.
     */
    List<CumulativeExpensePoint> cumulativeExpenses(UserId userId, ZoneId zone, DateRange period);

    /**
     * Un movimento per identificatore, ma solo fra quelli dell'utente indicato.
     *
     * L'utente è parte della domanda, non un controllo aggiunto dopo: chiedere un
     * movimento che non è suo deve dare "non esiste".
     */
    Optional<Movement> findById(UserId userId, MovementId id);

    /**
     * Scrive le sole tre cose che appartengono all'utente: descrizione,
     * pagatore/pagante e riclassificazione.
     */
    void updateEditable(Movement movement);

    /**
     * La somma dei movimenti per conto, in centesimi: il saldo corrente meno il
     * saldo iniziale del conto. Gli annullati non sono compresi.
     *
     * La somma la fa PostgreSQL. Portare i movimenti in memoria per addizionarli
     * costerebbe di trasferimento più di quanto costi tutta la query.
     */
    Map<AccountId, Long> sumByAccount(UserId userId);

    /**
     * Come {@link #sumByAccount}, ma in euro al cambio di ogni movimento: è ciò che si
     * somma fra conti di valute diverse.
     */
    Map<AccountId, ConvertedMovements> convertedByAccount(UserId userId);

    long countByUserAndSource(UserId userId, IngestionSource source);
}
