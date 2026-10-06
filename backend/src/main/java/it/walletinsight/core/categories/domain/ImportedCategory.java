package it.walletinsight.core.categories.domain;

import java.util.Objects;

/**
 * Una categoria letta da una sorgente, prima di essere salvata.
 *
 * {@code template} è la voce dell'elenco di base in cui l'adapter della sorgente
 * suggerisce di farla confluire ({@code cibo/spesa}): è la sola cosa che solo lui
 * sa, perché dipende da come quella sorgente nomina le proprie categorie.
 *
 * @param parentExternalId la categoria della sorgente da cui deriva, per quelle
 *                         create dall'utente nella sorgente
 * @param template         la voce dell'elenco di base suggerita, o {@code null}
 */
public record ImportedCategory(
        String externalId,
        String name,
        String group,
        String parentExternalId,
        String template) {

    public ImportedCategory {
        Objects.requireNonNull(externalId, "externalId");
        name = name == null || name.isBlank() ? externalId : name.trim();
    }
}
