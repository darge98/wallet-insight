
/**
 * Il client HTTP comune: ogni `data-access` di modulo lo usa per il proprio
 * adapter. Le classi repository invece non escono mai da qui, si inietta il token.
 */
export * from './lib/http/api-base-url';
export * from './lib/http/api-contract';
export * from './lib/http/current-user';
export * from './lib/http/json-request';
export * from './lib/http/shared-http.providers';
