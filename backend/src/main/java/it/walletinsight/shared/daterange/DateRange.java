package it.walletinsight.shared.daterange;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Intervallo di date con entrambi gli estremi inclusi. */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Intervallo rovesciato: %s è precedente a %s.".formatted(to, from));
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    /** Durata in giorni, estremi inclusi: un intervallo di un giorno vale 1. */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1L;
    }

    /**
     * L'intervallo con cui confrontare questo.
     *
     * Un tratto di mese che parte dal giorno 1 si confronta con lo stesso tratto del
     * mese prima: l'1–4 ottobre con l'1–4 settembre, un mese intero col mese intero
     * precedente. Contro i quattro giorni subito prima, o contro tutto settembre, la
     * variazione direbbe solo quanto è avanti il mese, non come sta andando.
     *
     * Ogni altro intervallo si confronta con altrettanti giorni subito prima: su
     * «ultimi 7 giorni» il paragone giusto sono i sette precedenti.
     */
    public DateRange previousComparable() {
        YearMonth mese = YearMonth.from(from);
        if (from.getDayOfMonth() == 1 && mese.equals(YearMonth.from(to))) {
            YearMonth precedente = mese.minusMonths(1);
            // `minusMonths` si ferma all'ultimo giorno del mese prima: il 30 marzo dà il 28 febbraio.
            LocalDate fine = to.equals(mese.atEndOfMonth()) ? precedente.atEndOfMonth() : to.minusMonths(1);
            return new DateRange(precedente.atDay(1), fine);
        }
        long lunghezza = days();
        return new DateRange(from.minusDays(lunghezza), from.minusDays(1));
    }
}
