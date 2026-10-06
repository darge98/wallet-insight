package it.walletinsight.platform.web;

/**
 * Sollevata dai moduli di dominio quando un identificatore non esiste.
 *
 * Vive in `platform` e non nei singoli moduli perché `ApiExceptionHandler` deve
 * poterla gestire senza dipendere da nessun modulo di dominio: il contrario
 * creerebbe un ciclo.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String id;

    public ResourceNotFoundException(String resourceType, String id) {
        super("%s non trovato: %s.".formatted(resourceType, id));
        this.resourceType = resourceType;
        this.id = id;
    }

    public String resourceType() {
        return resourceType;
    }

    public String id() {
        return id;
    }
}
