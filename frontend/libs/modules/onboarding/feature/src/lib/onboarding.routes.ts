import { Routes } from '@angular/router';

export const onboardingRoutes: Routes = [
  {
    path: '',
    title: 'Benvenuto · Wallet Insights',
    loadComponent: () => import('./pages/onboarding-page').then((m) => m.OnboardingPage),
  },
];
