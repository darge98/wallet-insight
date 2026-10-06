package it.walletinsight.core.accounts.application;

import it.walletinsight.core.accounts.domain.Account;
import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.accounts.domain.AccountRepository;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.platform.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Casi d'uso dei conti: elencarli e allinearli a ciò che una sorgente dichiara.
 *
 * {@link #syncFromSource} è idempotente per costruzione: riconosce un conto dal
 * riferimento all'originale (`source` + `externalId`), quindi rigirare un import
 * non duplica nulla e non perde l'identificatore già assegnato — che è ciò a cui
 * i movimenti si appoggeranno.
 *
 * Un conto sparito dalla sorgente **non** viene cancellato. Possiede movimenti
 * storici, e il fatto che oggi BudgetBakers non lo elenchi più (cancellato,
 * oppure semplicemente non restituito da una chiamata andata storta) non è una
 * buona ragione per distruggere quello che ci abbiamo appeso.
 */
@Service
@Transactional
public class AccountService {

    private static final String USER_RESOURCE_TYPE = "Utente";
    private static final String ACCOUNT_RESOURCE_TYPE = "Conto";

    private final AccountRepository repository;
    private final UserRepository users;

    public AccountService(AccountRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts(UserId userId) {
        requireUser(userId);
        return repository.findByUser(userId);
    }

    /**
     * Cambia ciò che di un conto appartiene all'utente: il nome e il colore.
     *
     * Le due cose stanno insieme perché sono la stessa domanda — "come voglio
     * vedere questo conto" — e perché così restano una scrittura sola anche
     * quando l'interfaccia le cambia insieme. Un parametro {@code null} significa
     * *non toccare*, non *azzera*: è la semantica del PATCH da cui arriva, e non
     * esiste un modo per togliere il nome (non avrebbe senso) né per togliere il
     * colore (l'utente ne sceglie un altro).
     *
     * Sul nome: da qui in avanti gli import lasciano stare {@code name} — la
     * differenza fra i due nomi *è* il segno che l'utente è intervenuto. Il caso
     * limite è voluto: rinominare un conto esattamente com'è chiamato nella
     * sorgente lo rimette a seguirla, ed è il modo per tornare indietro senza un
     * secondo comando da inventare.
     *
     * Chiamarlo senza cambiare niente non scrive: `updated_at` non è un registro
     * di quante volte è stata aperta la schermata.
     */
    public Account updateAppearance(
            UserId userId, AccountId accountId, String newName, String newColor) {
        requireUser(userId);
        Account conto = repository.findById(userId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ACCOUNT_RESOURCE_TYPE, accountId.toString()));

        Account aggiornato = conto;
        if (newName != null) {
            aggiornato = aggiornato.renamedTo(newName);
        }
        if (newColor != null) {
            aggiornato = aggiornato.coloredWith(newColor);
        }
        if (aggiornato.equals(conto)) {
            return conto;
        }
        repository.update(aggiornato);
        return aggiornato;
    }

    /**
     * Allinea i conti di una sorgente: crea quelli nuovi, riallinea quelli noti.
     *
     * Gli {@code incoming} arrivano già nella forma del dominio, con un
     * identificatore appena generato che viene usato solo per i conti davvero
     * nuovi: per gli altri vince l'identificatore già in tabella.
     *
     * Restituisce lo stato risultante, nell'ordine in cui la sorgente li ha dati,
     * così chi ha chiamato può dire esattamente cos'è cambiato.
     */
    public List<Account> syncFromSource(
            UserId userId, IngestionSource source, List<Account> incoming) {
        requireUser(userId);

        List<Account> risultato = new ArrayList<>(incoming.size());
        for (Account dallaSorgente : incoming) {
            Optional<Account> esistente =
                    repository.findByExternalId(userId, source, dallaSorgente.externalId());
            if (esistente.isEmpty()) {
                repository.insert(dallaSorgente);
                risultato.add(dallaSorgente);
                continue;
            }
            Account riallineato = esistente.get().refreshedFrom(dallaSorgente);
            // Niente da scrivere se la sorgente non ha cambiato nulla: un import a
            // vuoto non deve muovere updated_at su tutti i conti dell'utente.
            if (!riallineato.equals(esistente.get())) {
                repository.update(riallineato);
            }
            risultato.add(riallineato);
        }
        return risultato;
    }

    private void requireUser(UserId userId) {
        if (users.findById(userId).isEmpty()) {
            throw new ResourceNotFoundException(USER_RESOURCE_TYPE, userId.toString());
        }
    }
}
