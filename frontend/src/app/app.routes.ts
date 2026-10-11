import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guards';
import { Role } from './core/models';

const MANAGERS: Role[] = ['ADMIN', 'OPERATOR'];
const ADMINS: Role[] = ['ADMIN'];

// Route-level roles mirror the REST API permissions of the specification.
export const routes: Routes = [
  {
    path: 'connexion',
    title: 'Connexion | Télérelève CEAC',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      {
        path: 'tableau-de-bord',
        title: 'Tableau de bord | Télérelève CEAC',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'carte',
        title: 'Carte | Télérelève CEAC',
        loadComponent: () => import('./features/map/map.page').then((m) => m.MapPage),
      },
      {
        path: 'compteurs',
        title: 'Compteurs | Télérelève CEAC',
        canActivate: [roleGuard],
        data: { roles: MANAGERS },
        loadComponent: () => import('./features/meters/meter-list.page').then((m) => m.MeterListPage),
      },
      {
        path: 'compteurs/:id',
        title: 'Détail du compteur | Télérelève CEAC',
        canActivate: [roleGuard],
        data: { roles: MANAGERS },
        loadComponent: () => import('./features/meters/meter-detail.page').then((m) => m.MeterDetailPage),
      },
      {
        path: 'alertes',
        title: 'Alertes | Télérelève CEAC',
        loadComponent: () => import('./features/alerts/alerts.page').then((m) => m.AlertsPage),
      },
      {
        path: 'administration',
        title: 'Administration | Télérelève CEAC',
        canActivate: [roleGuard],
        data: { roles: ADMINS },
        loadComponent: () => import('./features/admin/admin.page').then((m) => m.AdminPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
