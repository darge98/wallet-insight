/**
 * Seconda apertura pubblica del modulo: i casi d'uso, quelli che il BFF compone.
 *
 * Sta accanto a `domain` e non al posto suo: il BFF chiama i servizi, gli altri moduli di
 * dominio si fermano ai tipi. `infrastructure` resta privata in entrambi i casi.
 */
@org.springframework.modulith.NamedInterface("application")
package it.walletinsight.core.users.application;
