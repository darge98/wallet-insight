package it.walletinsight;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(WalletInsightApiApplication.class);

    @Test
    void verificaIConfiniFraModuli() {
        MODULES.verify();
    }

    @Test
    void stampaLaMappaDeiModuli() {
        MODULES.forEach(System.out::println);
    }
}
