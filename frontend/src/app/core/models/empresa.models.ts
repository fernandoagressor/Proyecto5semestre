import { CompanyModuleCode } from './company-module.models';

export interface Company {
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
  estado: string;
  limiteProyectos: number;
  limiteUsuarios: number;
  modulos: CompanyModuleCode[];
  version: number;
}

export interface Project {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string | null;
  ciudad: string;
  departamento: string;
  direccion: string | null;
  fechaInicio: string | null;
  fechaFinEstimada: string | null;
  estado: 'PLANEACION' | 'ACTIVO' | 'PAUSADO' | 'FINALIZADO' | 'INACTIVO';
  porcentajeAvance: number;
  maxLotes: number | null;
  imagenPrincipalUrl: string | null;
  gerenteId: number | null;
  creadoEn: string;
  actualizadoEn: string;
}
