/**
 * L'area `accounts` espone il proprio facade, come fanno `user` e `ingestion`.
 *
 * È ciò che permette alla sidebar, alla Panoramica e ai Movimenti di nominare i
 * conti senza che il tipo `Account` debba salire in `shared`: un conto è di
 * quest'area, e chi lo consuma passa di qui.
 */
export * from './lib/accounts-overview';
export * from './lib/http/accounts-http.providers';
