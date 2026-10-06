/**
 * Entry point secondario: `@wallet/shared-ui/echarts`.
 *
 * Isolato dal barrel principale perché va caricato in modo lazy — importarlo
 * dall'index trascinerebbe l'intera libreria ECharts nel bundle iniziale.
 */
export * from './lib/charts/echarts.setup';
