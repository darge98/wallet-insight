package it.walletinsight.core.movements.domain;

import java.util.List;

/**
 * Cosa ha fatto il salvataggio di una finestra importata.
 *
 * @param saved   movimenti creati o riallineati
 * @param removed movimenti in sospeso tolti perché la sorgente non li restituisce più
 */
public record ImportedWindow(int saved, List<Movement> removed) {

    public ImportedWindow {
        removed = List.copyOf(removed);
    }
}
