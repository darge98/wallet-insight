import { Routes } from '@angular/router';

import { Shell } from '@wallet/core-shell';

import { requireUserGuard, skipIfUserGuard } from './user.guards';

/**
 * Rotte applicative.
 *
 * L'app è il punto di composizione: monta le rotte che ogni feature espone dal
 * proprio barrel, senza conoscerne pagine o componenti. Aggiungere una sezione
 * significa aggiungere una libreria e una voce qui — nessun file esistente
 * cambia comportamento.
 *
 * L'onboarding è l'unica rotta fuori dalla shell: finché il profilo non esiste
 * non c'è navigazione da mostrare.
 */
export const appRoutes: Routes = [
  {
    path: 'onboarding',
    canMatch: [skipIfUserGuard],
    loadChildren: () => import('@wallet/onboarding-feature').then((m) => m.onboardingRoutes),
  },
  {
    path: '',
    canMatch: [requireUserGuard],
    component: Shell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'panoramica' },
      {
        path: 'panoramica',
        loadChildren: () => import('@wallet/overview-feature').then((m) => m.overviewRoutes),
      },
      {
        path: 'movimenti',
        loadChildren: () => import('@wallet/movements-feature').then((m) => m.movementsRoutes),
      },
      {
        path: 'budget',
        loadChildren: () => import('@wallet/budgets-feature').then((m) => m.budgetsRoutes),
      },
      {
        path: 'abbonamenti',
        loadChildren: () =>
          import('@wallet/subscriptions-feature').then((m) => m.subscriptionsRoutes),
      },
      {
        path: 'impostazioni',
        loadChildren: () => import('@wallet/settings-feature').then((m) => m.settingsRoutes),
      },
      {
        path: '',
        loadChildren: () => import('@wallet/upcoming-feature').then((m) => m.upcomingRoutes),
      },

      // Percorsi della versione precedente, mantenuti per non rompere i link salvati.
      { path: 'dashboard', redirectTo: 'panoramica' },
      { path: 'records', redirectTo: 'movimenti' },
    ],
  },
  { path: '**', redirectTo: '' },
];
