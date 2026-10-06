package it.walletinsight.core.users.domain;

/**
 * Periodo predefinito della panoramica.
 *
 * Le costanti corrispondono esattamente ai preset del frontend
 * (`DASHBOARD_PERIOD_PRESETS`): `KebabCase` le traduce in `current-month`,
 * `last-7-days`, ecc. Se il frontend aggiunge un preset, va aggiunto anche qui.
 */
public enum DashboardPeriod {
    TODAY, CURRENT_WEEK, CURRENT_MONTH, CURRENT_YEAR, LAST_7_DAYS, LAST_30_DAYS
}
