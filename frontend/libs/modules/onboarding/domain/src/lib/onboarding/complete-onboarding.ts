import { ConfigureImportConnectionCommand, ImportConnection } from '@wallet/ingestion-domain';
import { CreateUserProfileCommand, UserProfile } from '@wallet/user-domain';

/**
 * Il primo accesso, per intero.
 *
 * Profilo e sorgente sono due scritture ma un gesto solo: modellarle come un
 * comando unico è ciò che permette al backend di eseguirle in transazione, e
 * quindi di non lasciare mai un utente senza la sorgente che ha appena scelto.
 *
 * `importConnection` è `null` quando l'utente rimanda la scelta: rimandare è una
 * decisione legittima, non un errore.
 */
export interface CompleteOnboardingCommand {
  readonly profile: CreateUserProfileCommand;
  readonly importConnection: ConfigureImportConnectionCommand | null;
}

/**
 * Ciò che esiste dopo il primo accesso: il profilo e le sorgenti collegate.
 *
 * Le connessioni arrivano con il primo import già fatto: `lastRecordDate` e
 * `lastRunAt` sono valorizzate, a meno che quell'import non sia andato storto.
 * È l'unico segnale che il primo accesso dà sull'importazione, e basta: non c'è
 * più un «aggiorna ora» da premere, aggiornare tocca alla schedulazione.
 */
export interface OnboardingResult {
  readonly profile: UserProfile;
  readonly connections: readonly ImportConnection[];
}
