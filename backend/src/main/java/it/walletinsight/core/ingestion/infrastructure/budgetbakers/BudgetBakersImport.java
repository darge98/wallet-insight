package it.walletinsight.core.ingestion.infrastructure.budgetbakers;

import it.walletinsight.core.ingestion.application.ImportService;
import it.walletinsight.core.ingestion.domain.ImportConnection;
import it.walletinsight.shared.source.IngestionSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * L'import schedulato di BudgetBakers per tutti gli utenti che l'hanno collegato.
 *
 * All'avvio importa subito, senza aspettare il prossimo giro: chi riaccende
 * l'applicazione vuole i dati di adesso, e un import incrementale costa tre
 * chiamate. È un di più: se fallisce lo registra e lascia partire l'applicazione.
 */
@Component
class BudgetBakersImport {

    private static final Logger log = LoggerFactory.getLogger(BudgetBakersImport.class);

    private static final String CRON = "${margine.budgetbakers.import.cron}";
    private static final String ZONE = "${margine.budgetbakers.import.zone}";

    /**
     * Sotto questa distanza dall'ultimo import riuscito l'avvio non importa.
     *
     * Protegge il limite di BudgetBakers (300 richieste l'ora per token) da un
     * container che riparte in ciclo: senza, ogni riavvio costerebbe da tre a undici
     * chiamate, e qualche decina di riavvii basterebbe a farsi revocare il token.
     * Con la soglia l'avvio ne spende al più una dozzina l'ora.
     */
    static final Duration INTERVALLO_MINIMO = Duration.ofMinutes(15);

    private final ImportService imports;
    private final String pianificazione;

    BudgetBakersImport(ImportService imports, @Value(CRON) String pianificazione) {
        this.imports = imports;
        this.pianificazione = pianificazione;
    }

    @Scheduled(cron = CRON, zone = ZONE)
    void importaTuttiGliUtenti() {
        imports.importForAllUsers(IngestionSource.BUDGET_BAKERS);
    }

    @EventListener(ApplicationReadyEvent.class)
    void importaAllAvvio() {
        if (Scheduled.CRON_DISABLED.equals(pianificazione)) {
            return;
        }
        Instant adesso = Instant.now();
        try {
            imports.importForAllUsers(IngestionSource.BUDGET_BAKERS,
                    connection -> daAggiornare(connection, adesso));
        } catch (RuntimeException e) {
            log.error("Import di BudgetBakers all'avvio fallito: {}", e.getMessage(), e);
        }
    }

    /** Mai importata, o importata l'ultima volta da almeno {@link #INTERVALLO_MINIMO}. */
    static boolean daAggiornare(ImportConnection connection, Instant adesso) {
        return connection.lastRunAt() == null
                || !connection.lastRunAt().plus(INTERVALLO_MINIMO).isAfter(adesso);
    }
}
