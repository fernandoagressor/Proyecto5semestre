// Gerencia y control: metas comerciales, comisiones, presupuestos, costos y rentabilidad.
// Espejo de dto/gerencia/ComisionMetaDtos.java y FinanzasDtos.java.

export type GoalStatus = 'BORRADOR' | 'APROBADA' | 'CERRADA';

export interface Goal {
  id: number;
  proyectoId: number | null;
  asesorId: number | null;
  /** AAAA-MM */
  periodo: string;
  valorMeta: number;
  cantidadMeta: number;
  estado: GoalStatus;
  aprobadoPor: number | null;
  version: number;
}

export interface GoalRequest {
  proyectoId: number | null;
  asesorId: number | null;
  periodo: string;
  valorMeta: number;
  cantidadMeta: number;
}

export interface GoalProgress {
  metaId: number;
  periodo: string;
  proyectoId: number | null;
  asesorId: number | null;
  valorMeta: number;
  valorLogrado: number;
  porcentajeValor: number;
  cantidadMeta: number;
  cantidadLograda: number;
  porcentajeCantidad: number;
  estado: GoalStatus;
}

export type CommissionRuleType = 'PORCENTAJE' | 'VALOR_FIJO';

export interface CommissionRule {
  id: number;
  proyectoId: number | null;
  nombre: string;
  tipo: CommissionRuleType;
  valor: number;
  condicionCausacion: string;
  vigenteDesde: string;
  vigenteHasta: string | null;
  activo: boolean;
  version: number;
}

export interface CommissionRuleRequest {
  proyectoId: number | null;
  nombre: string;
  tipo: CommissionRuleType;
  valor: number;
  condicionCausacion: string;
  vigenteDesde: string;
  vigenteHasta: string | null;
}

export type CommissionStatus = 'PROYECTADA' | 'CAUSADA' | 'APROBADA' | 'PAGADA' | 'ANULADA';

export interface Commission {
  id: number;
  proyectoId: number;
  asesorId: number;
  contratoId: number;
  reglaComisionId: number;
  baseCalculo: number;
  porcentaje: number | null;
  valorComision: number;
  estado: CommissionStatus;
  fechaCausacion: string | null;
  fechaPago: string | null;
  aprobadoPor: number | null;
  version: number;
}

export type CostCategoryType = 'DIRECTO' | 'INDIRECTO';

export interface CostCategory {
  id: number;
  codigo: string;
  nombre: string;
  tipo: CostCategoryType;
  activo: boolean;
}

export type BudgetStatus = 'BORRADOR' | 'APROBADO' | 'CERRADO' | 'ANULADO';

export interface Budget {
  id: number;
  proyectoId: number;
  categoriaCostoId: number;
  periodoDesde: string;
  periodoHasta: string;
  valorPresupuestado: number;
  estado: BudgetStatus;
  aprobadoPor: number | null;
  creadoPor: number | null;
  version: number;
}

export interface BudgetRequest {
  proyectoId: number;
  categoriaCostoId: number;
  periodoDesde: string;
  periodoHasta: string;
  valorPresupuestado: number;
}

export type CostStatus = 'REGISTRADO' | 'APROBADO' | 'PAGADO' | 'ANULADO';

export interface Cost {
  id: number;
  proyectoId: number;
  categoriaCostoId: number;
  fechaCosto: string;
  concepto: string;
  proveedor: string | null;
  referencia: string | null;
  valor: number;
  comprobanteId: number | null;
  estado: CostStatus;
  registradoPor: number | null;
  aprobadoPor: number | null;
  version: number;
}

export interface CostRequest {
  proyectoId: number;
  categoriaCostoId: number;
  fechaCosto: string;
  concepto: string;
  proveedor: string | null;
  referencia: string | null;
  valor: number;
}

export interface Profitability {
  proyectoId: number | null;
  desde: string;
  hasta: string;
  ventas: number;
  recaudo: number;
  presupuestoAprobado: number;
  costosAprobados: number;
  desviacionPresupuesto: number;
  utilidad: number;
  margen: number | null;
}
