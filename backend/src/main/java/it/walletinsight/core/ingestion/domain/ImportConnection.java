package it.walletinsight.core.ingestion.domain;

import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.source.ImportCredentialKind;
import it.walletinsight.shared.source.IngestionSource;
import it.walletinsight.shared.source.KebabName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Una sorgente collegata da un utente: l'associazione fra chi importa e da dove.
 *
 * Un utente può averne più d'una, al massimo una per sorgente. Ricollegare la
 * stessa sorgente non crea un secondo legame, ne sostituisce le credenziali:
 * è ciò che rende sicuro riprovare un onboarding interrotto a metà.
 *
 * Il {@link PersonalToken} fa parte dell'aggregato in chiaro perché il dominio
 * ragiona sul segreto, non sulla sua forma cifrata: cifrare è una scelta di
 * persistenza e vive in `infrastructure`. Verso l'esterno esce solo
 * {@link PersonalToken#hint()}.
 *
 * {@code lastRecordDate} è il segnaposto dell'importazione incrementale: la data
 * del dato più recente già portato dentro, `null` finché non se n'è mai letto
 * nessuno. È un fatto della connessione e non del singolo job perché è ciò che
 * sopravvive fra un'esecuzione e l'altra: il job lo legge per sapere da dove
 * ripartire e lo riscrive quando ha finito.
 *
 * {@code lastRunAt} è l'altra faccia e va tenuta distinta: l'istante in cui si è
 * *guardato*, non fino a quando si ha il dato. Un import che gira e non trova
 * nulla sposta il secondo e lascia fermo il primo — ed è proprio quella
 * differenza a rispondere alla domanda "sono aggiornato?", perché sapere che
 * dieci minuti fa non c'era niente di nuovo è una risposta, mentre non saperlo
 * lascia il dubbio che sia tutto rotto.
 */
public record ImportConnection(
        ImportConnectionId id,
        UserId userId,
        IngestionSource source,
        PersonalToken token,
        boolean enabled,
        LocalDate configuredAt,
        LocalDate lastRecordDate,
        Instant lastRunAt,
        Instant credentialsRejectedAt) {

    public ImportConnection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(configuredAt, "configuredAt");
        if (!source.available()) {
            throw new IllegalArgumentException(
                    "La sorgente %s non è ancora disponibile.".formatted(KebabName.of(source)));
        }
        if (source.credential() != ImportCredentialKind.PERSONAL_TOKEN) {
            throw new IllegalArgumentException(
                    "La sorgente %s non si configura con un token.".formatted(KebabName.of(source)));
        }
    }

    /** Una connessione mai importata: nessun segnaposto e nessuna esecuzione alle spalle. */
    public ImportConnection(
            ImportConnectionId id,
            UserId userId,
            IngestionSource source,
            PersonalToken token,
            boolean enabled,
            LocalDate configuredAt) {
        this(id, userId, source, token, enabled, configuredAt, null, null);
    }

    public ImportConnection(
            ImportConnectionId id,
            UserId userId,
            IngestionSource source,
            PersonalToken token,
            boolean enabled,
            LocalDate configuredAt,
            LocalDate lastRecordDate,
            Instant lastRunAt) {
        this(id, userId, source, token, enabled, configuredAt, lastRecordDate, lastRunAt, null);
    }

    /** Collega per la prima volta una sorgente: identificatore nuovo. */
    public static ImportConnection configure(
            UserId userId, IngestionSource source, PersonalToken token, boolean enabled, LocalDate today) {
        return new ImportConnection(
                ImportConnectionId.newId(), userId, source, token, enabled, today);
    }

    /**
     * Copia con credenziali sostituite: l'identificatore resta, la data di configurazione no.
     *
     * Il segnaposto sopravvive: cambiare il token è un fatto sulle credenziali, non
     * sullo storico già letto. Azzerarlo farebbe rileggere tutto da capo ogni volta
     * che l'utente incolla un token nuovo.
     */
    public ImportConnection reconfigure(PersonalToken newToken, boolean newEnabled, LocalDate today) {
        return new ImportConnection(
                id, userId, source, newToken, newEnabled, today, lastRecordDate, lastRunAt, null);
    }

    /**
     * Copia con il segnaposto portato fino alla data indicata.
     *
     * Il segnaposto non torna mai indietro: una data più vecchia di quella corrente
     * viene ignorata invece di sollevare. Un import che rilegge una finestra già
     * coperta — o che gira due volte fuori ordine — è normale e non è un errore;
     * arretrare il segnaposto significherebbe invece rileggere all'infinito.
     */
    public ImportConnection importedThrough(LocalDate recordDate) {
        Objects.requireNonNull(recordDate, "recordDate");
        if (lastRecordDate != null && !recordDate.isAfter(lastRecordDate)) {
            return this;
        }
        return new ImportConnection(
                id, userId, source, token, enabled, configuredAt, recordDate, lastRunAt,
                credentialsRejectedAt);
    }

    /**
     * Copia con l'istante dell'ultima esecuzione riuscita.
     *
     * A differenza del segnaposto questo avanza sempre, anche quando non è
     * arrivato nessun dato: è il senso stesso del campo. Lo scrive solo un import
     * che arriva in fondo — un tentativo fallito non è una sincronizzazione, e
     * registrarlo comunque farebbe leggere "aggiornato" a chi non lo è.
     */
    public ImportConnection ranAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return new ImportConnection(
                id, userId, source, token, enabled, configuredAt, lastRecordDate, instant, null);
    }

    /** Copia con le credenziali rifiutate dalla sorgente: finché l'utente non le sostituisce, l'import non riesce. */
    public ImportConnection credentialsRejected(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return new ImportConnection(
                id, userId, source, token, enabled, configuredAt, lastRecordDate, lastRunAt, instant);
    }

    /** `true` se la sorgente non è mai stata letta: il prossimo import parte dall'inizio. */
    public boolean neverImported() {
        return lastRecordDate == null;
    }
}
