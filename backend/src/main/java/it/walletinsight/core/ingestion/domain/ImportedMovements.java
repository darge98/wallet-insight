package it.walletinsight.core.ingestion.domain;

import it.walletinsight.core.movements.domain.Movement;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Il risultato di una lettura dalla sorgente: i movimenti e la finestra in cui li si e' cercati.
 *
 * I movimenti sono gia' aggregati di `core.movements`, non un tipo di passaggio:
 * e' l'adapter della sorgente a tradurli, come fa con i conti, perche' e' l'unico
 * che sa com'e' fatta la sorgente. Qui resta solo la contabilita' della finestra.
 *
 * {@link #lastRecordDate()} e' il nuovo segnaposto da far sapere al backend, ed e'
 * la data del movimento piu' recente *trovato*, non l'estremo della finestra
 * richiesta: se in questo giro non e' arrivato nulla il segnaposto non deve
 * muoversi, altrimenti un movimento registrato in ritardo su un giorno gia'
 * superato non verrebbe mai piu' letto.
 *
 * @param from        primo giorno letto, incluso
 * @param toExclusive primo giorno non letto
 * @param movements   i movimenti tradotti, nell'ordine in cui l'API li ha resi
 */
public record ImportedMovements(LocalDate from, LocalDate toExclusive, List<Movement> movements) {

    public ImportedMovements {
        movements = List.copyOf(movements);
    }

    /**
     * Il giorno UTC del movimento piu' recente letto; vuoto se non ne e' arrivato nessuno.
     *
     * In UTC e non nel fuso dell'utente: e' il segnaposto da cui ripartire, e la
     * finestra chiesta alla sorgente si esprime in date UTC.
     */
    public Optional<LocalDate> lastRecordDate() {
        return movements.stream()
                .map(Movement::recordedAt)
                .max(Comparator.naturalOrder())
                .map(istante -> istante.atZone(ZoneOffset.UTC).toLocalDate());
    }

    public boolean isEmpty() {
        return movements.isEmpty();
    }

    public int size() {
        return movements.size();
    }
}
