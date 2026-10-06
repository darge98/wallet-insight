import { Routes } from '@angular/router';

/**
 * Le rotte della sezione.
 *
 * Il guscio è una rotta senza percorso con figli: i percorsi delle pagine sono
 * relativi, quindi chi monta la sezione decide dove vive senza che la feature lo
 * sappia. `Piano` non ha una rotta — è annunciata nel menu e non è raggiungibile,
 * perché una pagina che nessuno può aprire sarebbe codice che nessuno esegue.
 */
export const settingsRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/settings-layout').then((m) => m.SettingsLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'profilo' },
      {
        path: 'profilo',
        title: 'Profilo · Wallet Insights',
        loadComponent: () => import('./pages/profile-page').then((m) => m.ProfilePage),
      },
      {
        path: 'categorie',
        title: 'Categorie · Wallet Insights',
        loadComponent: () => import('./pages/categories-page').then((m) => m.CategoriesPage),
      },
      {
        path: 'conti',
        title: 'Conti · Wallet Insights',
        loadComponent: () => import('./pages/accounts-page').then((m) => m.AccountsPage),
      },
    ],
  },
];
