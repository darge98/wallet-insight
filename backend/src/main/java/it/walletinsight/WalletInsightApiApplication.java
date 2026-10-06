package it.walletinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * `shared` e `platform` sono dichiarati condivisi: ogni modulo può usarli senza
 * dichiararli fra le proprie dipendenze, perché non contengono dominio.
 */
@Modulithic(sharedModules = { "shared", "platform" })
@SpringBootApplication
public class WalletInsightApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletInsightApiApplication.class, args);
    }
}
