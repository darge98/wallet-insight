package it.walletinsight.core.subscriptions.domain;

import java.time.temporal.ChronoUnit;

public enum CadenceUnit {
    WEEK(ChronoUnit.WEEKS),
    MONTH(ChronoUnit.MONTHS),
    YEAR(ChronoUnit.YEARS);

    private final ChronoUnit chronoUnit;

    CadenceUnit(ChronoUnit chronoUnit) {
        this.chronoUnit = chronoUnit;
    }

    ChronoUnit chronoUnit() {
        return chronoUnit;
    }
}
