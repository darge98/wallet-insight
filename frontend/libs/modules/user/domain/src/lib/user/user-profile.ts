import { DashboardPeriodPreset } from '@wallet/shared-util';

/**
 * Identificatore tipizzato del profilo.
 *
 * Il meccanismo dei branded type è lo stesso del resto del dominio, ma il tipo
 * `UserId` appartiene al dominio Utente: non deve stare in `shared` solo perché
 * è condiviso il *pattern*.
 */
declare const userIdBrand: unique symbol;

export type UserId = string & { readonly [userIdBrand]: 'User' };

export const asUserId = (raw: string): UserId => raw as UserId;

export const USER_LANGUAGES = ['it', 'en'] as const;

export type UserLanguage = (typeof USER_LANGUAGES)[number];

/** Nome della lingua nella lingua stessa: non va tradotto. */
export const USER_LANGUAGE_LABELS: Readonly<Record<UserLanguage, string>> = {
  it: 'Italiano',
  en: 'English',
};

/**
 * Profilo dell'utente e sue preferenze.
 *
 * `firstName` è l'unico dato obbligatorio: cognome ed email restano opzionali e
 * vengono normalizzati a `null`, così l'assenza di un valore è esplicita e non
 * si confonde con una stringa vuota.
 */
export interface UserProfile {
  readonly id: UserId;
  readonly firstName: string;
  readonly lastName: string | null;
  readonly email: string | null;
  readonly timeZone: string;
  readonly language: UserLanguage;
  readonly defaultDashboardPeriod: DashboardPeriodPreset;
}

/** Dati richiesti per creare il profilo la prima volta. */
export interface CreateUserProfileCommand {
  readonly firstName: string;
  readonly lastName: string | null;
  readonly email: string | null;
  readonly timeZone: string;
  readonly language: UserLanguage;
  readonly defaultDashboardPeriod: DashboardPeriodPreset;
}

/** Nome da mostrare nell'interfaccia: "Nome Cognome", oppure il solo nome. */
export function displayName(profile: UserProfile): string {
  return [profile.firstName, profile.lastName].filter((part) => part !== null).join(' ');
}
