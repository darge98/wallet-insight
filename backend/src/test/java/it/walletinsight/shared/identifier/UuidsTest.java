package it.walletinsight.shared.identifier;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidsTest {

    @Test
    void produceUnUuidDiVersione7ConLaVarianteGiusta() {
        UUID uuid = Uuids.v7();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void incorporaIlTimestampNeiQuarantottoBitPiuSignificativi() {
        long prima = System.currentTimeMillis();
        UUID uuid = Uuids.v7();
        long dopo = System.currentTimeMillis();

        assertThat(Uuids.timestampMillisOf(uuid)).isBetween(prima, dopo);
    }

    @Test
    void unTimestampPiuRecenteProduceUnUuidPiuGrande() {
        // Il confronto lessicografico degli UUIDv7 è cronologico: è ciò che rende
        // gli indici B-tree sequenziali e l'ordinamento per ID un ordinamento per creazione.
        assertThat(Uuids.v7(1_000L)).isLessThan(Uuids.v7(2_000L));
        assertThat(Uuids.v7(1_700_000_000_000L)).isLessThan(Uuids.v7(1_700_000_000_001L));
    }

    @Test
    void dueUuidDelloStessoMillisecondoRestanoDistinti() {
        assertThat(Uuids.v7(42L)).isNotEqualTo(Uuids.v7(42L));
    }

    @Test
    void serializzaNelFormatoCanonico() {
        UUID uuid = Uuids.v7(0L);

        assertThat(UUID.fromString(uuid.toString())).isEqualTo(uuid);
    }
}
