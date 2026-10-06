import { Routes } from '@angular/router';

/**
 * Rotte delle sezioni annunciate.
 *
 * Una sola pagina parametrica: la chiave arriva dai `data` della rotta e viene
 * legata all'input del componente da `withComponentInputBinding()`.
 */
export const upcomingRoutes: Routes = [
  {
    path: 'assistente',
    title: 'Assistente AI · Wallet Insights',
    data: { section: 'assistant' },
    loadComponent: () => import('./pages/upcoming-page').then((m) => m.UpcomingPage),
  },
];
