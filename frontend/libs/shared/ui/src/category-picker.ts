/**
 * Entry point secondario: `@wallet/shared-ui/category-picker`.
 *
 * Fuori dal barrel principale perché si porta dietro `@angular/aria` e l'overlay
 * del CDK: dall'index finirebbero nel bundle iniziale, e lo usano solo pagine lazy.
 */
export * from './lib/components/category-picker';
