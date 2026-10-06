package it.walletinsight.support;

import it.walletinsight.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base per i test che toccano il database.
 *
 * Il container Postgres è gestito da {@link TestcontainersConfiguration}, importata qui:
 * Spring Boot lo avvia e lo collega al datasource tramite {@code @ServiceConnection},
 * riusando il ciclo di vita già gestito dal contesto invece di duplicarlo con un
 * container statico e {@code @DynamicPropertySource}.
 */
@SpringBootTest(properties = {
        // Chiave di prova: il contesto non parte senza, e in un test la vogliamo fissa
        // per poter verificare che ciò che finisce nel database sia davvero cifrato.
        "margine.encryption.key=ZbUqo79prBAq2EbR4QsHQ1ATxQvRmqCOmq1dZva3Ggo=",
        // Spenta anche il recupero all'avvio, che altrimenti chiamerebbe la sorgente vera.
        "margine.budgetbakers.import.cron=-"
})
@Import(TestcontainersConfiguration.class)
public abstract class AbstractDatabaseTest {

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void pulisciLeTabelle() {
        // Ogni test parte da database vuoto: l'elenco cresce con le tabelle dei moduli.
        jdbc.sql("truncate table users, import_connections, accounts, source_categories, categories, movements, budgets, subscriptions cascade")
                .update();
    }
}
