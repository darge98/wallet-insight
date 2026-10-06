import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';

import { ONBOARDING_REPOSITORY } from '@wallet/onboarding-domain';

import { HttpOnboardingRepository } from './http-onboarding.repository';

export function provideOnboardingHttp(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: ONBOARDING_REPOSITORY, useClass: HttpOnboardingRepository },
  ]);
}
