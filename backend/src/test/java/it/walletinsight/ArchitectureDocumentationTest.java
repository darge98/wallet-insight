package it.walletinsight;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(Path.of("build", "spring-modulith-docs")).isNotEmptyDirectory();
    }
}
