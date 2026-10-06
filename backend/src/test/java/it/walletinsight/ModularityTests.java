package it.walletinsight;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(WalletInsightApiApplication.class);

    @Test
    void verificaIConfiniFraModuli() {
        MODULES.verify();
    }

    @Test
    void stampaLaMappaDeiModuli() {
        assertThat(MODULES).isNotEmpty();
        MODULES.forEach(System.out::println);
    }
}
