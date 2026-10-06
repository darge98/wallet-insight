import { Routes } from '@angular/router';

export const movementsRoutes: Routes = [
  {
    path: '',
    title: 'Movimenti · Wallet Insights',
    loadComponent: () => import('./pages/movements-page').then((m) => m.MovementsPage),
  },
];
