import { CompanyModuleCode } from '../models/company-module.models';

export type NavigationGroup = 'inicio' | 'plataforma' | 'inventario' | 'ventas' | 'cobro' | 'gestion';

export interface NavigationItem {
  label: string;
  caption: string;
  route: string;
  icon: string;
  group: NavigationGroup;
  permissions: string[];
  /** 'all' exige todos los permisos (igual que `permissionMode` en la ruta); por defecto basta uno. */
  permissionMode?: 'any' | 'all';
  module?: CompanyModuleCode;
}

/**
 * Orden y títulos de los grupos del menú lateral. Siguen el camino de la venta: 1) armar el
 * proyecto y sus lotes, 2) vender (prospecto → simulación → reserva → contrato), 3) cobrar.
 * El de inicio no lleva título.
 */
export const NAVIGATION_GROUPS: readonly { id: NavigationGroup; label: string }[] = [
  { id: 'inicio', label: '' },
  { id: 'plataforma', label: 'Plataforma' },
  { id: 'inventario', label: '1 · Proyectos' },
  { id: 'ventas', label: '2 · Vender' },
  { id: 'cobro', label: '3 · Cobrar' },
  { id: 'gestion', label: 'Gestión' },
];

export const NAVIGATION_ITEMS: readonly NavigationItem[] = [
  {
    label: 'Panel general',
    caption: 'Cómo va la empresa hoy',
    route: '/dashboard',
    icon: 'dashboard',
    group: 'inicio',
    permissions: ['DASHBOARD_VER'],
  },
  {
    label: 'Empresas',
    caption: 'Empresas, usuarios y accesos',
    route: '/superadmin',
    icon: 'building',
    group: 'plataforma',
    permissions: ['ROLE_SUPER_ADMIN'],
  },
  {
    label: 'Proyectos',
    caption: 'Crea el proyecto y sus etapas',
    route: '/proyectos',
    icon: 'projects',
    group: 'inventario',
    permissions: ['PROYECTOS_VER'],
    module: 'PROYECTOS_INVENTARIO',
  },
  {
    label: 'Lotes',
    caption: 'Manzanas, lotes, precios y reservas',
    route: '/inventario',
    icon: 'inventory',
    group: 'inventario',
    permissions: ['LOTES_VER'],
    module: 'PROYECTOS_INVENTARIO',
  },
  {
    label: 'Prospectos',
    caption: 'Interesados, seguimiento y citas',
    route: '/crm',
    icon: 'crm',
    group: 'ventas',
    permissions: ['PROSPECTOS_VER', 'OPORTUNIDADES_VER'],
    module: 'CLIENTES_VENTAS',
  },
  {
    label: 'Simulador',
    caption: 'Cuotas para cualquier persona',
    route: '/simulador',
    icon: 'calculator',
    group: 'ventas',
    permissions: ['SIMULACIONES_VER'],
    module: 'CLIENTES_VENTAS',
  },
  {
    label: 'Reservas y contratos',
    caption: 'Reserva pagada → contrato → firma',
    route: '/ventas',
    icon: 'sales',
    group: 'ventas',
    permissions: ['RESERVAS_VER', 'CONTRATOS_VER'],
    module: 'CLIENTES_VENTAS',
  },
  {
    label: 'Cartera',
    caption: 'Cuotas, pagos y mora',
    route: '/cartera',
    icon: 'wallet',
    group: 'cobro',
    permissions: ['CUOTAS_VER', 'PAGOS_VER'],
    module: 'CARTERA_PAGOS',
  },
  {
    label: 'Gerencia',
    caption: 'Metas, comisiones y rentabilidad',
    route: '/gerencia',
    icon: 'management',
    group: 'gestion',
    permissions: ['METAS_VER', 'COMISIONES_EQUIPO_VER', 'RENTABILIDAD_VER', 'REPORTES_EXPORTAR'],
    module: 'GERENCIA_REPORTES',
  },
  {
    label: 'Servicio',
    caption: 'PQRS, citas y soporte',
    route: '/servicio',
    icon: 'service',
    group: 'gestion',
    permissions: ['SOLICITUDES_VER', 'CITAS_VER'],
    module: 'PORTAL_SERVICIO',
  },
  {
    label: 'Usuarios y roles',
    caption: 'Cuentas, roles y permisos',
    route: '/usuarios',
    icon: 'users',
    group: 'gestion',
    permissions: ['USUARIOS_VER', 'ROLES_VER'],
    module: 'ADMINISTRACION_SEGURIDAD',
  },
  {
    label: 'Auditoría',
    caption: 'Quién hizo qué y cuándo',
    route: '/auditoria',
    icon: 'audit',
    group: 'gestion',
    permissions: ['AUDITORIA_VER'],
    module: 'ADMINISTRACION_SEGURIDAD',
  },
];
