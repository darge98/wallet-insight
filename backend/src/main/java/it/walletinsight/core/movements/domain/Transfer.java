package it.walletinsight.core.movements.domain;

import java.util.Objects;

/**
 * Il fatto che un movimento sia una gamba di un giroconto.
 *
 * Serve perché un trasferimento fra due conti dell'utente non è né una spesa né
 * un'entrata: sul saldo complessivo le due gambe si annullano, ma su "quanto ho
 * speso questo mese" contarle sarebbe un errore. Senza questo segno le due cose
 * sarebbero indistinguibili da una spesa e da un incasso.
 *
 * {@code externalId} lega le due gambe e può mancare anche quando il
 * trasferimento c'è: succede in 14 movimenti su 1672, dichiarati come giroconto
 * ma senza identificativo condiviso. La gamba resta riconoscibile come tale, non
 * accoppiabile alla sua controparte.
 *
 * @param state      accoppiato o meno con l'altra gamba
 * @param externalId identificativo condiviso dalle due gambe, può essere null
 */
public record Transfer(TransferState state, String externalId) {

    public Transfer {
        Objects.requireNonNull(state, "state");
        externalId = externalId == null || externalId.isBlank() ? null : externalId.trim();
    }

    public boolean isPairable() {
        return externalId != null;
    }

    /** Stato di accoppiamento delle due gambe. */
    public enum TransferState {
        PAIRED,
        UNPAIRED,
        UNKNOWN
    }
}
