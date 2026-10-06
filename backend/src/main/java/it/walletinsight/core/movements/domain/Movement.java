package it.walletinsight.core.movements.domain;

import it.walletinsight.core.accounts.domain.AccountId;
import it.walletinsight.core.categories.domain.CategoryId;
import it.walletinsight.core.users.domain.UserId;
import it.walletinsight.shared.money.CurrencyCode;
import it.walletinsight.shared.money.Money;
import it.walletinsight.shared.source.IngestionSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Un movimento di denaro su un conto dell'utente.
 *
 * L'importo è firmato: negativo in uscita, positivo in entrata. Il saldo di un
 * conto è la somma di questi importi a partire dal saldo iniziale, ed è per
 * questo che un movimento non può esistere senza il conto su cui poggia —
 * {@code accountId} è quello di Wallet Insights, non quello della sorgente, che resta
 * nel conto come riferimento all'originale.
 *
 * <h2>Cosa riscrive un import e cosa no</h2>
 *
 * Importo, data, verso, stato e giroconto sono fatti avvenuti altrove: ogni
 * import li riscrive, e correggerli qui creerebbe una verità che il giro
 * successivo cancellerebbe.
 *
 * Descrizione, pagatore/pagante e categoria no. L'import le scrive **una volta
 * sola**, quando il movimento entra, e da lì in poi sono del movimento: nessun
 * giro successivo le tocca, e chi guarda può correggerle e svuotarle. La ragione
 * è misurata sui dati reali — la descrizione della sorgente è spesso il
 * tracciato della banca ({@code PAGAMENTO DEBINT PAGAMENTO CARTA DI DEBITO
 * INTERNAZIONALE 49...}) e il pagatore manca su più di un terzo dei movimenti,
 * dove nessun import lo riempirà mai.
 *
 * <h2>Quando</h2>
 *
 * {@code recordedAt} è l'istante come lo dà la sorgente, in UTC: il giorno dipende da
 * chi guarda, e lo decide {@link #dateIn} col fuso del suo profilo. Molti movimenti
 * arrivano a mezzanotte UTC perché la sorgente ne conosce solo il giorno: in un fuso a
 * ovest di Greenwich cadrebbero il giorno prima, un limite noto e accettato.
 *
 * <h2>Due importi</h2>
 *
 * {@code amount} è nella valuta del conto, e su quello si calcola il saldo del
 * conto. {@code convertedAmount} è lo stesso movimento in euro al cambio del suo
 * giorno, e su quello si calcolano tutti i totali: sommare dollari ed euro come
 * se fossero la stessa cosa darebbe un numero che non vuol dire niente.
 *
 * Sono un valore ciascuna e non una coppia «sorgente più utente», come invece
 * erano fra V9 e V11. Con due valori e un ripiego in lettura {@code null} non
 * poteva voler dire «vuoto» — voleva già dire «segui l'altro» — e svuotare un
 * campo era impossibile. Con un valore solo torna a voler dire quello che vuol
 * dire ovunque, e l'import resta un solo {@code upsert} in batch che tre colonne
 * non le nomina proprio.
 */
public record Movement(
        MovementId id,
        UserId userId,
        AccountId accountId,
        IngestionSource source,
        String externalId,
        Money amount,
        Money convertedAmount,
        Instant recordedAt,
        MovementDirection direction,
        MovementState state,
        String description,
        String counterParty,
        Classification classification,
        Transfer transfer) {

    public static final int MAX_EXTERNAL_ID_LENGTH = 120;

    /** La valuta di {@code convertedAmount}, e quindi di ogni totale. */
    public static final CurrencyCode TOTALS_CURRENCY = CurrencyCode.DEFAULT;

    public Movement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(convertedAmount, "convertedAmount");
        if (convertedAmount.currency() != TOTALS_CURRENCY) {
            throw new IllegalArgumentException(
                    "L'importo convertito deve essere in %s, non in %s."
                            .formatted(TOTALS_CURRENCY, convertedAmount.currency()));
        }
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(state, "state");
        externalId = requireExternalId(externalId);
        // Assente e vuoto sono la stessa cosa: un movimento senza descrizione e uno
        // con la descrizione fatta di spazi non sono due situazioni diverse.
        description = blankToNull(description);
        counterParty = blankToNull(counterParty);
        classification = classification == null ? Classification.none() : classification;
    }

    /** Un movimento già in euro: l'importo convertito è l'importo stesso. */
    public Movement(MovementId id, UserId userId, AccountId accountId, IngestionSource source,
                    String externalId, Money amount, Instant recordedAt, MovementDirection direction,
                    MovementState state, String description, String counterParty,
                    Classification classification, Transfer transfer) {
        this(id, userId, accountId, source, externalId, amount, amount, recordedAt, direction, state,
                description, counterParty, classification, transfer);
    }

    /** Un movimento appena letto da una sorgente: identificatore nuovo, niente ancora dell'utente. */
    public static Movement imported(
            UserId userId,
            AccountId accountId,
            IngestionSource source,
            String externalId,
            Money amount,
            Money convertedAmount,
            Instant recordedAt,
            MovementDirection direction,
            MovementState state,
            String description,
            String counterParty,
            Classification classification,
            Transfer transfer) {
        return new Movement(MovementId.newId(), userId, accountId, source, externalId, amount,
                convertedAmount, recordedAt, direction, state, description, counterParty, classification,
                transfer);
    }

    /** Copia con un'altra descrizione; un testo vuoto la lascia senza. */
    public Movement describedAs(String text) {
        return withEdits(text, counterParty, classification);
    }

    /**
     * Copia con un altro pagatore/pagante: la persona o il negozio a cui il
     * movimento si riferisce. Un testo vuoto lo lascia senza.
     */
    public Movement attributedTo(String counterPartyName) {
        return withEdits(description, counterPartyName, classification);
    }

    /** Copia spostata su un'altra categoria. */
    public Movement classifiedAs(CategoryId category) {
        return withEdits(description, counterParty, classification.movedTo(category));
    }

    /**
     * Riallinea il movimento a ciò che la sorgente dice oggi, conservando identità
     * e interventi dell'utente.
     *
     * Non lo usa l'import — quello scrive in batch con una sola istruzione SQL che
     * fa la stessa cosa dentro il database — ma è qui perché è la regola, e averla
     * scritta in Java è ciò che permette di verificarla senza un container.
     */
    public Movement refreshedFrom(Movement fromSource) {
        return new Movement(
                id,
                userId,
                fromSource.accountId,
                source,
                externalId,
                fromSource.amount,
                fromSource.convertedAmount,
                fromSource.recordedAt,
                fromSource.direction,
                fromSource.state,
                // I tre campi restano quelli di questo movimento, non quelli che la
                // sorgente porta adesso: li ha scritti l'import la prima volta, e da
                // allora sono suoi. È la stessa cosa che fa la `on conflict do update`,
                // che queste tre colonne non le nomina.
                description,
                counterParty,
                classification,
                fromSource.transfer);
    }

    /**
     * `true` se le tre cose correggibili si possono correggere davvero.
     *
     * Un movimento ancora da contabilizzare no, e la ragione è misurata: quando la
     * banca lo conferma, la sorgente non lo aggiorna — ne crea **uno nuovo, con un
     * altro identificativo**, e fa sparire quello in sospeso. Tutto ciò che vi fosse
     * stato scritto sopra morirebbe con lui, senza che nessuno l'abbia chiesto.
     *
     * Impedirlo non toglie niente a chi guarda: basta aspettare un giorno e
     * correggere il movimento definitivo, che è anche l'unico che resterà.
     */
    public boolean editable() {
        return state != MovementState.UNCLEARED;
    }

    /** Il giorno del movimento nel calendario di chi guarda. */
    public LocalDate dateIn(ZoneId zone) {
        return recordedAt.atZone(zone).toLocalDate();
    }

    /** `true` se il movimento è una gamba di un giroconto fra due conti. */
    public boolean isTransfer() {
        return transfer != null;
    }

    /**
     * `true` se questo movimento pesa sul saldo.
     *
     * Solo gli annullati non pesano. Un movimento non ancora riconciliato o in
     * attesa di assegnazione è denaro che si è mosso davvero: non contarlo
     * darebbe un saldo che non corrisponde all'estratto conto.
     */
    public boolean countsTowardsBalance() {
        return state != MovementState.VOID;
    }

    private Movement withEdits(
            String newDescription, String newCounterParty, Classification newClassification) {
        return new Movement(id, userId, accountId, source, externalId, amount, convertedAmount,
                recordedAt, direction, state, newDescription, newCounterParty, newClassification, transfer);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireExternalId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Il campo externalId è obbligatorio.");
        }
        if (trimmed.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Il campo externalId supera i %d caratteri.".formatted(MAX_EXTERNAL_ID_LENGTH));
        }
        return trimmed;
    }
}
