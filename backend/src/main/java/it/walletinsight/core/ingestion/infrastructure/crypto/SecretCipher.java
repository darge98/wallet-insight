package it.walletinsight.core.ingestion.infrastructure.crypto;

import it.walletinsight.platform.web.CorruptedDataException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifratura simmetrica dei segreti prima che tocchino il database.
 *
 * AES-256 in modalità GCM: cifra *e* autentica, quindi una riga modificata a mano
 * non si decifra in silenzio ma fallisce. Il nonce è casuale e diverso a ogni
 * scrittura — riusarlo con la stessa chiave in GCM rivelerebbe il testo in chiaro —
 * e viaggia in testa al valore, perché per decifrare serve e segreto non è.
 *
 * Il contesto passato a {@code encrypt}/{@code decrypt} diventa dato autenticato
 * aggiuntivo (AAD): il valore cifrato resta valido solo nella riga in cui è nato,
 * e copiarlo nella riga di un altro utente lo rende indecifrabile.
 *
 * Il formato è prefissato dalla versione: il giorno in cui la chiave va ruotata o
 * l'algoritmo cambiato, le righe vecchie restano riconoscibili e convertibili.
 */
public final class SecretCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String VERSION = "1";
    private static final String SEPARATOR = ":";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final SecretKey key;

    SecretCipher(String base64Key) {
        this.key = parseKey(base64Key);
    }

    /** `versione:base64(nonce || testo cifrato e autenticato)`. */
    public String encrypt(String plaintext, String context) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        RANDOM.nextBytes(nonce);

        byte[] encrypted = apply(Cipher.ENCRYPT_MODE, nonce, context,
                plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] payload = new byte[nonce.length + encrypted.length];
        System.arraycopy(nonce, 0, payload, 0, nonce.length);
        System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
        return VERSION + SEPARATOR + ENCODER.encodeToString(payload);
    }

    /**
     * Un valore che non si decifra è un dato corrotto — chiave sbagliata, riga
     * manomessa, contesto diverso da quello di origine — non una richiesta
     * malformata: {@link CorruptedDataException} lo manda nei log e restituisce
     * al client un 500 generico, senza rivelare nulla del segreto.
     */
    public String decrypt(String value, String context) {
        String[] parts = value.split(SEPARATOR, 2);
        if (parts.length != 2 || !VERSION.equals(parts[0])) {
            throw new CorruptedDataException(
                    "Segreto cifrato in un formato sconosciuto: '%s'.".formatted(parts[0]), null);
        }

        byte[] payload = decode(parts[1]);
        if (payload.length <= NONCE_LENGTH_BYTES) {
            throw new CorruptedDataException("Segreto cifrato troncato.", null);
        }

        byte[] nonce = Arrays.copyOf(payload, NONCE_LENGTH_BYTES);
        byte[] encrypted = Arrays.copyOfRange(payload, NONCE_LENGTH_BYTES, payload.length);
        return new String(apply(Cipher.DECRYPT_MODE, nonce, context, encrypted), StandardCharsets.UTF_8);
    }

    private byte[] apply(int mode, byte[] nonce, String context, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException cause) {
            if (mode == Cipher.DECRYPT_MODE) {
                throw new CorruptedDataException(
                        "Segreto cifrato non decifrabile con la chiave corrente.", cause);
            }
            throw new IllegalStateException("Cifratura del segreto fallita.", cause);
        }
    }

    private static byte[] decode(String value) {
        try {
            return DECODER.decode(value);
        } catch (IllegalArgumentException cause) {
            throw new CorruptedDataException("Segreto cifrato non in base64.", cause);
        }
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("""
                    Manca la chiave di cifratura dei segreti (ENCRYPTION_KEY).
                    Generane una per questa istanza con: openssl rand -base64 32""");
        }

        byte[] material;
        try {
            material = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException cause) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY non è in base64: generala con `openssl rand -base64 32`.",
                    cause);
        }
        if (material.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY deve essere di %d byte (AES-256), ne ha %d."
                            .formatted(KEY_LENGTH_BYTES, material.length));
        }
        return new SecretKeySpec(material, ALGORITHM);
    }
}
