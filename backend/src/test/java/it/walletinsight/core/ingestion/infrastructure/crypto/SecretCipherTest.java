package it.walletinsight.core.ingestion.infrastructure.crypto;

import it.walletinsight.platform.web.CorruptedDataException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String CHIAVE = "ZbUqo79prBAq2EbR4QsHQ1ATxQvRmqCOmq1dZva3Ggo=";
    private static final String ALTRA_CHIAVE = "RKJksWmFTV7G61bgmxx6WepLg2TxdZDySO+8t+938VI=";
    private static final String SEGRETO = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ0YSJ9.aBcD1234";
    private static final String CONTESTO = "019b4c60-2f4f-7a01-9c19-0d6f3b4a8f4f:budget-bakers";

    private final SecretCipher cipher = new SecretCipher(CHIAVE);

    @Test
    void cifraEDecifraLoStessoSegreto() {
        String cifrato = cipher.encrypt(SEGRETO, CONTESTO);

        assertThat(cifrato).doesNotContain(SEGRETO).startsWith("1:");
        assertThat(cipher.decrypt(cifrato, CONTESTO)).isEqualTo(SEGRETO);
    }

    @Test
    void cifrareDueVolteProduceValoriDiversi() {
        // Nonce casuale a ogni scrittura: due righe con lo stesso token non si somigliano,
        // e riusare il nonce in GCM rivelerebbe il testo in chiaro.
        assertThat(cipher.encrypt(SEGRETO, CONTESTO))
                .isNotEqualTo(cipher.encrypt(SEGRETO, CONTESTO));
    }

    @Test
    void unAltraChiaveNonDecifra() {
        String cifrato = cipher.encrypt(SEGRETO, CONTESTO);

        assertThatThrownBy(() -> new SecretCipher(ALTRA_CHIAVE).decrypt(cifrato, CONTESTO))
                .isInstanceOf(CorruptedDataException.class);
    }

    @Test
    void unValoreCopiatoInUnAltraRigaNonDecifra() {
        String cifrato = cipher.encrypt(SEGRETO, CONTESTO);

        assertThatThrownBy(() -> cipher.decrypt(cifrato, "un-altro-utente:budget-bakers"))
                .isInstanceOf(CorruptedDataException.class);
    }

    @Test
    void unValoreManomessoNonDecifra() {
        String cifrato = cipher.encrypt(SEGRETO, CONTESTO);
        String manomesso = cifrato.substring(0, cifrato.length() - 2)
                + (cifrato.endsWith("A=") ? "B=" : "A=");

        assertThatThrownBy(() -> cipher.decrypt(manomesso, CONTESTO))
                .isInstanceOf(CorruptedDataException.class);
    }

    @Test
    void unFormatoSconosciutoEUnDatoCorrotto() {
        assertThatThrownBy(() -> cipher.decrypt("2:qualcosa", CONTESTO))
                .isInstanceOf(CorruptedDataException.class)
                .hasMessageContaining("formato sconosciuto");
    }

    @Test
    void senzaChiaveIlCifrarioNonNasce() {
        assertThatThrownBy(() -> new SecretCipher("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENCRYPTION_KEY");
    }

    @Test
    void rifiutaUnaChiaveDiLunghezzaSbagliata() {
        assertThatThrownBy(() -> new SecretCipher("dHJvcHBvIGNvcnRh"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }

    @Test
    void rifiutaUnaChiaveCheNonEBase64() {
        assertThatThrownBy(() -> new SecretCipher("non è base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}
