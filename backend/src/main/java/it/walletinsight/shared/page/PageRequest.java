package it.walletinsight.shared.page;

/**
 * Richiesta di pagina, a indice zero.
 *
 * I valori fuori range vengono riportati dentro i limiti invece di sollevare:
 * un `?page=-1` arrivato dal client è una richiesta della prima pagina, non un
 * errore da mostrare all'utente.
 */
public record PageRequest(int index, int size) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 200;

    public PageRequest {
        index = Math.max(index, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public static PageRequest of(int index, int size) {
        return new PageRequest(index, size);
    }

    public static PageRequest firstPage() {
        return new PageRequest(0, DEFAULT_SIZE);
    }

    public long offset() {
        return (long) index * size;
    }
}
