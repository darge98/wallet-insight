/**
 * Contesto degli utenti: anagrafica e impostazioni.
 *
 * Non espone HTTP: i suoi endpoint vivono in `bff`, che ne chiama i casi d'uso.
 *
 * Primo modulo sotto il namespace `core/`, che raggruppa i moduli di dominio per
 * non appiattirli accanto a `shared` e `platform`. Con la detection strategy
 * `explicitly-annotated` (in application.yaml) è modulo ciò che porta questa
 * annotazione, a qualsiasi profondità: dimenticarla significa non essere un modulo.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Users")
package it.walletinsight.core.users;
