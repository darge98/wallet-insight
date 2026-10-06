package it.walletinsight.platform.web;

import io.swagger.v3.oas.annotations.media.Schema;
import it.walletinsight.shared.money.Money;

/** Forma JSON di un importo: `{"amount": 4250, "currency": "EUR"}`. */
public record MoneyResponse(
        @Schema(
                description = "Importo intero in centesimi, non nell'unità principale della "
                        + "valuta: 4250 significa 42,50, non 4250.",
                example = "4250")
        long amount,
        String currency) {

    public static MoneyResponse from(Money money) {
        return new MoneyResponse(money.amount(), money.currency().name());
    }
}
