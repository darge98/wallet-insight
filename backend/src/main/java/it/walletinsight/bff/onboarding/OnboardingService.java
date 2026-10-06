package it.walletinsight.bff.onboarding;

import it.walletinsight.core.ingestion.application.ImportConnectionService;
import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.core.ingestion.domain.ImportOutcome;
import it.walletinsight.core.ingestion.domain.PersonalToken;
import it.walletinsight.core.users.domain.User;
import it.walletinsight.core.users.domain.UserSettings;
import it.walletinsight.shared.source.IngestionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L'unico caso d'uso che attraversa due domini: creare il profilo e collegare la
 * sorgente sono due scritture, ma per chi si iscrive sono un gesto solo.
 *
 * Sta nel BFF e non in `core/`: nessuno dei due moduli deve conoscere l'altro per
 * una sequenza che esiste solo in una schermata. Comporre qui significa che
 * l'onboarding può cambiare — un passo in più, uno in meno — senza toccare il
 * dominio.
 *
 * Chi collega una sorgente porta dentro anche i suoi dati, subito: l'import del
 * primo periodo parte da qui, mentre l'utente aspetta. È ciò che permette alla
 * panoramica di aprirsi su movimenti veri invece che su una schermata vuota con
 * un pulsante "aggiorna" da premere — pulsante che infatti non c'è più: da qui in
 * poi ad aggiornare sono le schedulazioni delle sorgenti, che chiamano lo stesso
 * {@link ImportService}.
 *
 * I confini transazionali sono due, non uno. Le scritture stanno in
 * {@link OnboardingRegistration}, che le commette insieme: un token rifiutato non
 * lascia dietro di sé un utente a metà, ed è la garanzia che il frontend non
 * poteva darsi da solo facendo due chiamate. L'import invece gira **fuori** da
 * quella transazione, e deve: dura secondi, parla con una sorgente esterna e ha
 * confini suoi: tenerlo dentro vorrebbe dire occupare una connessione al
 * database per tutto il tempo della rete.
 *
 * Un import che non riesce non fa fallire il primo accesso. Il profilo esiste, la
 * sorgente è collegata, e i dati arriveranno col prossimo giro: rifiutare tutto
 * perché una sorgente esterna non ha risposto costringerebbe a rifare da capo
 * un'iscrizione già valida.
 */
@Service
class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final OnboardingRegistration registration;
    private final ImportConnectionService connections;
    private final ImportService imports;

    OnboardingService(OnboardingRegistration registration,
                      ImportConnectionService connections,
                      ImportService imports) {
        this.registration = registration;
        this.connections = connections;
        this.imports = imports;
    }

    /** {@code source} e {@code token} sono nulli quando l'utente rimanda la scelta. */
    OnboardingResult complete(
            String firstName,
            String lastName,
            String email,
            UserSettings settings,
            IngestionSource source,
            PersonalToken token) {
        OnboardingResult registrato =
                registration.register(firstName, lastName, email, settings, source, token);

        if (registrato.connections().isEmpty()) {
            return registrato;
        }

        List<ImportOutcome> esiti = imports.importFor(registrato.user().id());
        esiti.stream().filter(esito -> !esito.succeeded()).forEach(esito -> log.warn(
                "Primo import {} non riuscito per l'utente {}: {}",
                esito.source(), registrato.user().id(), esito.failure()));

        // Rilette e non riusate: l'import ha appena spostato il segnaposto e
        // l'istante dell'ultima esecuzione, e restituire la connessione di prima
        // direbbe "mai importata" di una sorgente da cui i dati sono già dentro.
        return new OnboardingResult(
                registrato.user(), connections.listConnections(registrato.user().id()));
    }

    record OnboardingResult(User user, List<ImportConnection> connections) {
    }
}
