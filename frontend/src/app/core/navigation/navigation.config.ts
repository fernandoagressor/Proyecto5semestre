import { CompanyModuleCode } from '../models/company-module.models';

export interface NavigationItem {
  label: string;
  caption: string;
  route: string;
  icon: string;
  permissions: string[];
  module?: CompanyModuleCode;
}

export const NAVIGATION_ITEMS: readonly NavigationItem[] = [
  {
    label: 'Dashboard',
    caption: 'Indicadores en tiempo real',
    route: '/dashboard',
    icon: 'dashboard',
    permissions: ['DASHBOARD_VER'],
  },
  {
    label: 'Superadministración',
    caption: 'Empresas, usuarios y accesos',
    route: '/superadmin',
    icon: 'admin',
    permissions: ['ROLE_SUPER_ADMIN'],
  },
  {
    label: 'Proyectos',
    caption: 'Portafolio inmobiliario',
    route: '/proyectos',
    icon: 'projects',
    permissions: ['PROYECTOS_VER'],
    module: 'PROYECTOS_INVENTARIO',
  },
  {
    label: 'Inventario',
    caption: 'Etapas, manzanas y lotes',
    route: '/inventario',
    icon: 'inventory',
    permissions: ['LOTES_VER'],
    module: 'PROYECTOS_INVENTARIO',
  },
  {
    label: 'Clientes y ventas',
    caption: 'Clientes, lotes y reservas',
    route: '/ventas',
    icon: 'sales',
    permissions: ['CLIENTES_VER', 'RESERVAS_VER', 'CONTRATOS_VER'],
    module: 'CLIENTES_VENTAS',
  },
  {
    label: 'Cartera',
    caption: 'Cuotas, pagos y mora',
    route: '/cartera',
    icon: 'wallet',
    permissions: ['CUOTAS_VER', 'PAGOS_VER'],
    module: 'CARTERA_PAGOS',
  },
  {
    label: 'Servicio al cliente',
    caption: 'PQRS, citas y soporte',
    route: '/servicio',
    icon: 'service',
    permissions: ['SOLICITUDES_VER', 'CITAS_VER', 'SOPORTE_VER'],
    module: 'PORTAL_SERVICIO',
  },
  {
    label: 'Gerencia y control',
    caption: 'Metas, comisiones y reportes',
    route: '/gerencia',
    icon: 'management',
    permissions: ['METAS_VER', 'COMISIONES_EQUIPO_VER', 'RENTABILIDAD_VER'],
    module: 'GERENCIA_REPORTES',
  },
  {
    label: 'Auditoría',
    caption: 'Eventos y alertas',
    route: '/auditoria',
    icon: 'audit',
    permissions: ['AUDITORIA_VER'],
    module: 'ADMINISTRACION_SEGURIDAD',
  },
];
