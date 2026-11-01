import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { companyModuleGuard } from './core/guards/company-module.guard';
import { permissionGuard } from './core/guards/permission.guard';
import { passwordChangeGuard } from './core/guards/password-change.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
    title: 'Iniciar sesión | Nexora Core',
  },
  {
    path: '',
    canActivate: [authGuard],
    canActivateChild: [passwordChangeGuard],
    loadComponent: () => import('./layout/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'seguridad/cambiar-password',
        data: { title: 'Cambiar contraseña' },
        loadComponent: () =>
          import('./features/auth/change-password.page').then((m) => m.ChangePasswordPage),
        title: 'Cambiar contraseña | Nexora Core',
      },
      {
        path: 'superadmin',
        canActivate: [permissionGuard],
        data: { permissions: ['ROLE_SUPER_ADMIN'], title: 'Superadministración' },
        loadComponent: () =>
          import('./features/admin/super-admin.page').then((m) => m.SuperAdminPage),
        title: 'Superadministración | Nexora Core',
      },
      {
        path: 'dashboard',
        canActivate: [permissionGuard],
        data: { permissions: ['DASHBOARD_VER'], title: 'Dashboard' },
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
        title: 'Dashboard | Nexora Core',
      },
      {
        path: 'proyectos',
        canActivate: [permissionGuard, companyModuleGuard],
        data: { permissions: ['PROYECTOS_VER'], module: 'PROYECTOS_INVENTARIO', title: 'Proyectos' },
        loadComponent: () =>
          import('./features/projects/projects.page').then((m) => m.ProjectsPage),
        title: 'Proyectos | Nexora Core',
      },
      {
        path: 'inventario',
        canActivate: [permissionGuard, companyModuleGuard],
        data: { permissions: ['LOTES_VER'], module: 'PROYECTOS_INVENTARIO', title: 'Inventario' },
        loadComponent: () =>
          import('./features/inventory/inventory.page').then((m) => m.InventoryPage),
        title: 'Inventario | Nexora Core',
      },
      {
        path: 'crm',
        canActivate: [permissionGuard, companyModuleGuard],
        data: {
          permissions: ['PROSPECTOS_VER', 'OPORTUNIDADES_VER'],
          module: 'CLIENTES_VENTAS',
          title: 'CRM comercial',
        },
        loadComponent: () => import('./features/crm/crm.page').then((m) => m.CrmPage),
        title: 'CRM comercial | Nexora Core',
      },
      {
        path: 'simulador',
        canActivate: [permissionGuard, companyModuleGuard],
        data: { permissions: ['SIMULACIONES_VER'], module: 'CLIENTES_VENTAS', title: 'Simulador' },
        loadComponent: () => import('./features/simulator/simulator.page').then((m) => m.SimulatorPage),
        title: 'Simulador | Nexora Core',
      },
      {
        path: 'clientes/nuevo',
        canActivate: [permissionGuard, companyModuleGuard],
        data: {
          permissions: [
            'CLIENTES_CREAR',
            'PROSPECTOS_CREAR',
            'PROSPECTOS_CALIFICAR',
            'OPORTUNIDADES_CREAR',
            'OPORTUNIDADES_EDITAR',
            'RESERVAS_CREAR',
            'PROYECTOS_VER',
            'LOTES_VER',
          ],
          permissionMode: 'all',
          module: 'CLIENTES_VENTAS',
          title: 'Crear cliente y vincular lote',
        },
        loadComponent: () =>
          import('./features/customer-lot-flow/customer-lot-flow.page').then(
            (m) => m.CustomerLotFlowPage,
          ),
        title: 'Crear cliente y vincular lote | Nexora Core',
      },
      {
        path: 'ventas',
        canActivate: [permissionGuard, companyModuleGuard],
        data: { permissions: ['RESERVAS_VER', 'CONTRATOS_VER'], module: 'CLIENTES_VENTAS', title: 'Ventas' },
        loadComponent: () =>
          import('./features/sales/sales.page').then((m) => m.SalesPage),
        title: 'Ventas | Nexora Core',
      },
      {
        path: 'cartera',
        canActivate: [permissionGuard, companyModuleGuard],
        data: { permissions: ['PAGOS_VER', 'CUOTAS_VER'], module: 'CARTERA_PAGOS', title: 'Cartera' },
        loadComponent: () =>
          import('./features/portfolio/portfolio.page').then((m) => m.PortfolioPage),
        title: 'Cartera | Nexora Core',
      },
      {
        path: 'servicio',
        canActivate: [permissionGuard, companyModuleGuard],
        data: { permissions: ['SOLICITUDES_VER', 'CITAS_VER'], module: 'PORTAL_SERVICIO', title: 'Servicio al cliente' },
        loadComponent: () =>
          import('./features/customer-service/customer-service.page').then(
            (m) => m.CustomerServicePage,
          ),
        title: 'Servicio al cliente | Nexora Core',
      },
      {
        path: 'gerencia',
        canActivate: [permissionGuard, companyModuleGuard],
        data: {
          permissions: ['METAS_VER', 'COMISIONES_EQUIPO_VER', 'RENTABILIDAD_VER'],
          module: 'GERENCIA_REPORTES',
          title: 'Gerencia y control',
        },
        loadComponent: () =>
          import('./features/management/management.page').then((m) => m.ManagementPage),
        title: 'Gerencia y control | Nexora Core',
      },
      {
        path: 'auditoria',
        canActivate: [permissionGuard, companyModuleGuard],
        data: { permissions: ['AUDITORIA_VER'], module: 'ADMINISTRACION_SEGURIDAD', title: 'Auditoría' },
        loadComponent: () => import('./features/audit/audit.page').then((m) => m.AuditPage),
        title: 'Auditoría | Nexora Core',
      },
      {
        path: 'sin-acceso',
        data: { title: 'Acceso restringido' },
        loadComponent: () =>
          import('./features/status/access-denied.page').then((m) => m.AccessDeniedPage),
        title: 'Acceso restringido | Nexora Core',
      },
    ],
  },
  {
    path: '**',
    loadComponent: () => import('./features/status/not-found.page').then((m) => m.NotFoundPage),
    title: 'Página no encontrada | Nexora Core',
  },
];
