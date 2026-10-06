package it.walletinsight.core.ingestion.domain;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.categories.domain.ImportedCategory;
import it.walletinsight.shared.source.IngestionSource;

import java.util.List;
import java.util.Map;

/**
 * La porta verso una sorgente esterna: come si legge da BudgetBakers, da PSD2,
 * da quello che verrà.
 *
 * Il dominio dichiara *cosa* serve — i conti, le categorie, i movimenti da un
 * certo punto in poi — e non sa come ci si arrivi. Gli adapter stanno in
 * `infrastructure`, uno per sorgente, e nessuno fuori di lì vede i DTO o gli
 * endpoint di chi li espone. È la stessa forma dei repository: una porta nel
 * dominio, un adapter privato che la implementa.
 *
 * Ogni adapter dichiara con {@link #source()} la sorgente che serve, e
 * l'application sceglie il proprio in base alla connessione da importare:
 * aggiungere PSD2 sarà aggiungere una classe, non toccare il caso d'uso.
 *
 * I tre metodi vanno chiamati in quest'ordine, e l'ordine è imposto dalle firme
 * invece che da un commento: {@link #readMovements} pretende i conti e le
 * categorie già allineati perché un movimento poggia su entrambi, e solo chi li
 * ha appena allineati sa quale identificativo della sorgente corrisponde a quale
 * riga di Wallet Insights.
 */
public interface ImportSource {

    /** La sorgente che questo adapter sa leggere. */
    IngestionSource source();

    /**
     * I conti dell'utente, già tradotti negli aggregati di Wallet Insights.
     *
     * Gli identificatori sono nuovi: sarà `core.accounts` a tenere quelli già in
     * tabella per i conti che conosce già.
     */
    List<Account> readAccounts(ImportConnection connection);

    /**
     * Le categorie dell'utente, già tradotte negli aggregati di Wallet Insights.
     *
     * Vale lo stesso dei conti: identificatori nuovi, e a riconoscere ciò che
     * esiste già è `core.categories` dal riferimento all'originale.
     *
     * È una lettura a sé e non la raccolta delle categorie incontrate nei
     * movimenti, che pure basterebbe visto che ogni movimento se la porta dietro
     * denormalizzata. La differenza si vede negli import incrementali: quelli
     * leggono un giorno solo, quindi vedrebbero due o tre categorie su settanta, e
     * una rinomina fatta su una categoria non usata quel giorno non arriverebbe
     * mai. Chiederle tutte costa una chiamata e le tiene allineate davvero.
     */
    List<ImportedCategory> readCategories(ImportConnection connection);

    /**
     * I movimenti da importare, a partire dal segnaposto della connessione.
     *
     * Da dove ripartire quando un segnaposto non c'è ancora lo decide l'adapter:
     * è una politica della sorgente, non del caso d'uso.
     *
     * Prende i conti già allineati e, per identificativo della sorgente, la
     * categoria di Wallet Insights a cui ogni categoria è agganciata: un movimento poggia
     * su un conto e su una categoria *di Wallet Insights*.
     */
    ImportedMovements readMovements(ImportConnection connection, List<Account> accounts,
                                    Map<String, CategoryId> categoriesByExternalId);
}
