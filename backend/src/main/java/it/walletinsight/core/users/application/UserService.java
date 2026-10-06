package it.walletinsight.core.users.application;

import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.core.users.domain.UserRepository;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.platform.web.ResourceNotFoundException;
import it.walletinsight.shared.page.Page;
import it.walletinsight.shared.page.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casi d'uso degli utenti: creazione, consultazione, aggiornamento, cancellazione
 * e gestione delle impostazioni.
 *
 * L'ID viene assegnato qui (UUIDv7) e non dal database: l'aggregato è completo già
 * in memoria e l'API può restituire subito il `Location` della risorsa creata.
 */
@Service
@Transactional
public class UserService {

    private static final String RESOURCE_TYPE = "Utente";

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User createUser(String firstName, String lastName, String email, UserSettings settings) {
        User user = new User(UserId.newId(), firstName, lastName, email, settings);
        repository.insert(user);
        return user;
    }

    @Transactional(readOnly = true)
    public User getUser(UserId id) {
        return requireUser(id);
    }

    @Transactional(readOnly = true)
    public Page<User> listUsers(PageRequest request) {
        return Page.of(repository.findAll(request), repository.count(), request);
    }

    public User updateUser(UserId id, String firstName, String lastName, String email, UserSettings settings) {
        User user = requireUser(id).withProfile(firstName, lastName, email).withSettings(settings);
        repository.update(user);
        return user;
    }

    public void deleteUser(UserId id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException(RESOURCE_TYPE, id.toString());
        }
    }

    @Transactional(readOnly = true)
    public UserSettings getSettings(UserId id) {
        return requireUser(id).settings();
    }

    public User updateSettings(UserId id, UserSettings settings) {
        User user = requireUser(id).withSettings(settings);
        repository.update(user);
        return user;
    }

    private User requireUser(UserId id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_TYPE, id.toString()));
    }
}
