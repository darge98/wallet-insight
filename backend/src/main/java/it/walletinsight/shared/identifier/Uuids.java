package it.walletinsight.shared.identifier;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generatore di UUIDv7 (RFC 9562): i primi 48 bit sono il timestamp in millisecondi,
 * il resto è casuale.
 *
 * Li usiamo come identificatori delle entità al posto dei sequenziali del database:
 * restano stabili quando i dati migrano da un ambiente all'altro e, essendo ordinati
 * per tempo, gli indici B-tree di PostgreSQL ricevono inserimenti sempre in coda
 * invece che in punti sparsi come con gli UUIDv4.
 *
 * La generazione spetta all'applicazione e non al database perché l'ID deve esistere
 * prima dell'insert: l'aggregato è completo e coerente già in memoria.
 */
public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;
    private static final long VERSION_7_BITS = 0x7000L;
    private static final long RAND_A_MASK = 0x0FFFL;
    private static final long VARIANT_BITS = 0x8000_0000_0000_0000L;
    private static final long RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

    private Uuids() {
    }

    public static UUID v7() {
        return v7(System.currentTimeMillis());
    }

    // Visibile per i test: fissare il timestamp rende verificabili i bit prodotti.
    static UUID v7(long timestampMillis) {
        long random = RANDOM.nextLong();
        long mostSignificant = (timestampMillis & TIMESTAMP_MASK) << 16;
        mostSignificant |= VERSION_7_BITS;
        mostSignificant |= (random >>> 52) & RAND_A_MASK;
        long leastSignificant = VARIANT_BITS | (random & RAND_B_MASK);
        return new UUID(mostSignificant, leastSignificant);
    }

    /** Il timestamp di creazione incorporato nell'ID: utile per diagnosi e test. */
    public static long timestampMillisOf(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
