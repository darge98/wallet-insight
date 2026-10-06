package it.walletinsight.shared.money;

/** Le valute supportate, allineate a `shared/currency.ts` del frontend. */
public enum CurrencyCode {
    EUR, USD, GBP, CHF;

    public static final CurrencyCode DEFAULT = EUR;
}
