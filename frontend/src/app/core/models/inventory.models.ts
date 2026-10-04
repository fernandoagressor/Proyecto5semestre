export type StageStatus = 'ACTIVA' | 'INACTIVA' | 'CERRADA';
export type LotStatus = 'DISPONIBLE' | 'RESERVADO' | 'VENDIDO' | 'BLOQUEADO';

export interface InventorySummary {
  total: number;
  disponibles: number;
  reservados: number;
  vendidos: number;
  bloqueados: number;
  valorDisponible: number;
}

export interface ProjectStage {
  id: number;
  proyectoId: number;
  codigo: string;
  nombre: string;
  secuencia: number;
  fechaInicio: string | null;
  fechaFin: string | null;
  estado: StageStatus;
}

export interface Block {
  id: number;
  proyectoId: number;
  etapaId: number;
  codigo: string;
  nombre: string;
  secuencia: number;
  estado: StageStatus;
}

export interface Lot {
  id: number;
  proyectoId: number;
  etapaId: number;
  manzanaId: number;
  codigo: string;
  numero: string;
  areaM2: number;
  frenteM: number | null;
  fondoM: number | null;
  valorLista: number;
  estado: LotStatus;
  disponible: boolean;
  ubicacionReferencia: string | null;
  descripcion: string | null;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
}

export interface StagePayload {
  codigo: string;
  nombre: string;
  secuencia: number;
  fechaInicio: string | null;
  fechaFin: string | null;
  estado: StageStatus;
}

export interface BlockPayload {
  codigo: string;
  nombre: string;
  secuencia: number;
  estado: StageStatus;
}

export interface CreateLotPayload {
  etapaId: number;
  manzanaId: number;
  codigo: string;
  numero: string;
  areaM2: number;
  frenteM: number | null;
  fondoM: number | null;
  valorLista: number;
  ubicacionReferencia: string | null;
  descripcion: string | null;
}

export interface UpdateLotPayload {
  codigo: string;
  numero: string;
  areaM2: number;
  frenteM: number | null;
  fondoM: number | null;
  ubicacionReferencia: string | null;
  descripcion: string | null;
}

export interface LotStateHistory {
  id: number;
  estadoAnterior: LotStatus | null;
  estadoNuevo: LotStatus;
  entidadOrigen: string;
  entidadOrigenId: number | null;
  motivo: string;
  cambiadoPor: number;
  creadoEn: string;
}

export interface LotPriceHistory {
  id: number;
  valorAnterior: number;
  valorNuevo: number;
  vigenteDesde: string;
  vigenteHasta: string | null;
  motivo: string;
  cambiadoPor: number;
}
