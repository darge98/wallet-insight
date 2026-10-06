package it.walletinsight;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter;

/**
 * Genera in `build/spring-modulith-docs` i diagrammi dei moduli e il canvas delle
 * dipendenze: la mappa resta aggiornata perché la produce la build, non una mano.
 */
class ArchitectureDocumentationTest {

    @Test
    void generaLaDocumentazioneDeiModuli() {
        new Documenter(ModularityTests.MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
