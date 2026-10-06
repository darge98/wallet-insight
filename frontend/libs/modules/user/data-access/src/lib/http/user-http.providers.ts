import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import { USER_PROFILE_REPOSITORY } from '@wallet/user-domain';

import { HttpUserProfileRepository } from './http-user-profile.repository';

export function provideUserHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: USER_PROFILE_REPOSITORY, useClass: HttpUserProfileRepository },
  ]);
}
