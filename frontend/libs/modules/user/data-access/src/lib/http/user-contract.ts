import { contractError } from '@wallet/shared-data-access';
import { DASHBOARD_PERIOD_PRESETS, DashboardPeriodPreset } from '@wallet/shared-util';
import { asUserId, USER_LANGUAGES, UserLanguage, UserProfile } from '@wallet/user-domain';

export interface UserResponse {
  readonly id: string;
  readonly firstName: string;
  readonly lastName?: string;
  readonly email?: string;
  readonly timeZone: string;
  readonly language: string;
  readonly defaultDashboardPeriod: string;
}

/** Il corpo di `PUT /users/{id}`: sostituzione completa, non modifica parziale. */
export interface UserRequest {
  readonly firstName: string;
  readonly lastName: string | null;
  readonly email: string | null;
  readonly timeZone: string;
  readonly language: string;
  readonly defaultDashboardPeriod: string;
}

export function toUserProfile(raw: UserResponse): UserProfile {
  return {
    id: asUserId(raw.id),
    firstName: raw.firstName,
    lastName: raw.lastName ?? null,
    email: raw.email ?? null,
    timeZone: raw.timeZone,
    language: toUserLanguage(raw.language),
    defaultDashboardPeriod: toDashboardPeriod(raw.defaultDashboardPeriod),
  };
}

/** Il profilo nella forma che il server si aspetta: `null` dove qui c'è assenza. */
export function toUserRequest(profile: UserProfile): UserRequest {
  return {
    firstName: profile.firstName,
    lastName: profile.lastName,
    email: profile.email,
    timeZone: profile.timeZone,
    language: profile.language,
    defaultDashboardPeriod: profile.defaultDashboardPeriod,
  };
}

function toUserLanguage(raw: string): UserLanguage {
  if (!(USER_LANGUAGES as readonly string[]).includes(raw)) {
    throw contractError('language', raw);
  }
  return raw as UserLanguage;
}

function toDashboardPeriod(raw: string): DashboardPeriodPreset {
  if (!(DASHBOARD_PERIOD_PRESETS as readonly string[]).includes(raw)) {
    throw contractError('defaultDashboardPeriod', raw);
  }
  return raw as DashboardPeriodPreset;
}
