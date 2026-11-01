import { CompanyModuleCode } from '../models/company-module.models';

export type NavigationGroup = 'inicio' | 'plataforma' | 'inventario' | 'ventas' | 'gestion';

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

/** Orden y títulos de los grupos del menú lateral (el de inicio no lleva título). */
export const NAVIGATION_GROUPS: readonly { id: NavigationGroup; label: string }[] = [
  { id: 'inicio', label: '' },
  { id: 'plataforma', label: 'Plataforma' },
  { id: 'inventario', label: 'Inventario' },
  { id: 'ventas', label: 'Ventas' },
  { id: 'gestion', label: 'Gestión' },
];

export const NAVIGATION_ITEMS: readonly NavigationItem[] = [
  {
    label: 'Dashboard',
    caption: 'Indicadores en tiempo real',
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
    caption: 'Portafolio inmobiliario',
    route: '/proyectos',
    icon: 'projects',
    group: 'inventario',
    permissions: ['PROYECTOS_VER'],
    module: 'PROYECTOS_INVENTARIO',
  },
  {
    label: 'Lotes',
    caption: 'Etapas, manzanas y lotes',
    route: '/inventario',
    icon: 'inventory',
    group: 'inventario',
    permissions: ['LOTES_VER'],
    module: 'PROYECTOS_INVENTARIO',
  },
  {
    label: 'Comercial',
    caption: 'Prospectos, oportunidades y citas',
    route: '/crm',
    icon: 'crm',
    group: 'ventas',
    permissions: ['PROSPECTOS_VER', 'OPORTUNIDADES_VER'],
    module: 'CLIENTES_VENTAS',
  },
  {
    label: 'Nuevo cliente y lote',
    caption: 'Cliente, lote y reserva en un paso',
    route: '/clientes/nuevo',
    icon: 'plus',
    group: 'ventas',
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
  },
  {
    label: 'Simulador',
    caption: 'Plan de pagos para el cliente',
    route: '/simulador',
    icon: 'calculator',
    group: 'ventas',
    permissions: ['SIMULACIONES_VER'],
    module: 'CLIENTES_VENTAS',
  },
  {
    label: 'Reservas y contratos',
    caption: 'Clientes, reservas, contratos y desistimientos',
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
    group: 'ventas',
    permissions: ['CUOTAS_VER', 'PAGOS_VER'],
    module: 'CARTERA_PAGOS',
  },
  {
    label: 'Gerencia',
    caption: 'Metas, comisiones y rentabilidad',
    route: '/gerencia',
    icon: 'management',
    group: 'gestion',
    permissions: ['METAS_VER', 'COMISIONES_EQUIPO_VER', 'RENTABILIDAD_VER'],
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
    caption: 'Eventos y alertas',
    route: '/auditoria',
    icon: 'audit',
    group: 'gestion',
    permissions: ['AUDITORIA_VER'],
    module: 'ADMINISTRACION_SEGURIDAD',
  },
];
