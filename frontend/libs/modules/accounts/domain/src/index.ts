// Il conto reale, quello che arriva dal backend. Da non confondere con
// `DemoAccount` di `@wallet/shared-domain`, che è il conto del dataset
// dimostrativo su cui girano ancora movimenti e analisi.
export * from './lib/account/account';

// Porta: il contratto che l'adapter deve soddisfare
export * from './lib/account/account.port';
