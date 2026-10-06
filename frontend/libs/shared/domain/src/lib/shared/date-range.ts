/**
 * Il vocabolario di calendario è stato estratto nel kernel `@wallet/shared-util`
 * perché serve sia al dominio sia a un dominio separato (Utente) senza che i due
 * si conoscano. Qui resta solo il re-export, così gli import interni al dominio
 * continuano a funzionare invariati.
 */
export * from '@wallet/shared-util';
