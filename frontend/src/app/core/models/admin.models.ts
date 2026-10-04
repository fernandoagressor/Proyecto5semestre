import { Project } from './empresa.models';
import { CompanyModuleCode } from './company-module.models';

export interface GlobalAdminSummary {
  empresas: number;
  empresasActivas: number;
  empresasSuspendidas: number;
  empresasInactivas: number;
  proyectos: number;
  usuarios: number;
  usuariosActivos: number;
}

export interface GlobalAnalyticsSeries {
  categoria: string;
  valor: number;
  cantidad: number;
}

export interface GlobalCompanyAnalytics {
  empresaId: number;
  codigo: string;
  nombre: string;
  estado: CompanyStatus;
  proyectos: number;
  usuarios: number;
  lotes: number;
  reservasActivas: number;
  contratosVigentes: number;
  ventas: number;
  recaudo: number;
  cartera: number;
  carteraVencida: number;
}

export interface GlobalAdminAnalytics {
  ventas: number;
  recaudo: number;
  cartera: number;
  carteraVencida: number;
  inventarioDisponible: number;
  lotes: number;
  lotesDisponibles: number;
  lotesReservados: number;
  lotesVendidos: number;
  lotesBloqueados: number;
  reservasActivas: number;
  contratosVigentes: number;
  empresas: GlobalCompanyAnalytics[];
  lotesPorEstado: GlobalAnalyticsSeries[];
  proyectosPorEstado: GlobalAnalyticsSeries[];
  ventasMensuales: GlobalAnalyticsSeries[];
  recaudoMensual: GlobalAnalyticsSeries[];
}

export type CompanyStatus = 'ACTIVA' | 'SUSPENDIDA' | 'INACTIVA';

export interface AdminCompany {
  id: number;
  codigo: string;
  nombre: string;
  nit: string;
  direccion: string | null;
  ciudad: string | null;
  departamento: string | null;
  telefono: string | null;
  correo: string | null;
  logoUrl: string | null;
  zonaHoraria: string;
  moneda: string;
  estado: CompanyStatus;
  limiteProyectos: number;
  limiteUsuarios: number;
  modulos: CompanyModuleCode[];
  totalProyectos: number;
  totalUsuarios: number;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
}

export interface InitialAdministratorInput {
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string | null;
  nombreUsuario: string;
  correo: string;
  passwordTemporal: string;
}

export interface CompanyCreateInput {
  codigo: string;
  nombre: string;
  nit: string;
  direccion: string | null;
  ciudad: string | null;
  departamento: string | null;
  telefono: string | null;
  correo: string | null;
  logoUrl: string | null;
  zonaHoraria: string;
  moneda: string;
  limiteProyectos: number;
  limiteUsuarios: number;
  modulos: CompanyModuleCode[];
  administrador: InitialAdministratorInput;
}

export type CompanyUpdateInput = Omit<CompanyCreateInput, 'administrador'> & { version: number };

export interface AdminScope {
  id: number;
  tipo: 'EMPRESA' | 'PROYECTO' | 'PROPIOS';
  proyectoId: number | null;
  proyectoNombre: string | null;
}

export interface AdminUser {
  id: number;
  empresaId: number;
  personaId: number;
  nombreCompleto: string;
  nombres: string;
  apellidos: string | null;
  tipoDocumento: string;
  numeroDocumento: string;
  nombreUsuario: string;
  correo: string;
  telefono: string | null;
  estado: 'ACTIVO' | 'INACTIVO' | 'BLOQUEADO';
  requiereCambioPassword: boolean;
  mfaHabilitado: boolean;
  ultimoAccesoEn: string | null;
  rolIds: number[];
  roles: string[];
  alcances: AdminScope[];
  version: number;
}

export interface ScopeInput {
  tipo: 'EMPRESA' | 'PROYECTO' | 'PROPIOS';
  proyectoId: number | null;
}

export interface AdminUserCreateInput {
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string | null;
  telefono: string | null;
  nombreUsuario: string;
  correo: string;
  passwordTemporal: string;
  rolIds: number[];
  alcances: ScopeInput[];
}

export interface AdminUserUpdateInput {
  nombres: string;
  apellidos: string | null;
  telefono: string | null;
  correo: string;
  rolIds: number[];
  alcances: ScopeInput[];
  version: number;
}

export interface AdminRole {
  id: number;
  empresaId: number | null;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  sistema: boolean;
  protegido: boolean;
  activo: boolean;
  permisos: string[];
}

export interface PermissionItem {
  id: number;
  codigo: string;
  modulo: string;
  nombre: string;
  descripcion: string | null;
  sensible: boolean;
}

export interface RoleInput {
  codigo: string;
  nombre: string;
  descripcion: string | null;
  activo: boolean;
  permisoIds: number[];
}

export interface ProjectInput {
  codigo: string;
  nombre: string;
  descripcion: string | null;
  ciudad: string;
  departamento: string;
  direccion: string | null;
  fechaInicio: string | null;
  fechaFinEstimada: string | null;
  maxLotes: number | null;
  imagenPrincipalUrl: string | null;
  gerenteId: number | null;
}

export type AdminProject = Project;
