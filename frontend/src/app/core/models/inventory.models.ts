export type StageStatus = 'ACTIVA' | 'INACTIVA' | 'CERRADA';
/** PENDIENTE: lote generado en bloque, aún sin medidas o precio (no se puede reservar). */
export type LotStatus = 'PENDIENTE' | 'DISPONIBLE' | 'RESERVADO' | 'VENDIDO' | 'BLOQUEADO';

export interface InventorySummary {
  total: number;
  disponibles: number;
  reservados: number;
  vendidos: number;
  bloqueados: number;
  pendientes: number;
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
  /** Vacío (null) solo en lotes PENDIENTES. */
  areaM2: number;
  frenteM: number | null;
  fondoM: number | null;
  /** Vacío (null) solo en lotes PENDIENTES. */
  valorLista: number;
  estado: LotStatus;
  disponible: boolean;
  ubicacionReferencia: string | null;
  descripcion: string | null;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
}

/** Código y secuencia vacíos = automáticos (E01…). `cantidadManzanas` solo al crear. */
export interface StagePayload {
  codigo: string | null;
  nombre: string;
  secuencia: number | null;
  fechaInicio: string | null;
  fechaFin: string | null;
  estado: StageStatus;
  cantidadManzanas?: number | null;
}

/** Código y secuencia vacíos = automáticos (M01…). */
export interface BlockPayload {
  codigo: string | null;
  nombre: string;
  secuencia: number | null;
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
  areaM2: number | null;
  frenteM: number | null;
  fondoM: number | null;
  ubicacionReferencia: string | null;
  descripcion: string | null;
  /** Si cambia, queda en el historial de precios; en un lote PENDIENTE es el primer precio. */
  valorLista?: number | null;
  motivoPrecio?: string | null;
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
