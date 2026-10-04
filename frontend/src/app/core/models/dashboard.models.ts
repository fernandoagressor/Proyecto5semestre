export interface DashboardSeries {
  categoria: string;
  valor: number;
  cantidad: number;
}

export interface DashboardDateRange {
  desde: string;
  hasta: string;
}

export interface ManagementDashboard {
  desde: string;
  hasta: string;
  proyectoId: number | null;
  ventas: number;
  contratos: number;
  recaudo: number;
  carteraTotal: number;
  carteraVencida: number;
  indiceMora: number;
  costos: number;
  utilidad: number;
  margen: number;
  lotesDisponibles: number;
  inventarioDisponible: number;
  oportunidadesAbiertas: number;
  tareasVencidas: number;
  pqrsAbiertas: number;
}

export interface CommercialDashboard {
  desde: string;
  hasta: string;
  proyectoId: number | null;
  oportunidadesNuevas: number;
  oportunidadesGanadas: number;
  oportunidadesPerdidas: number;
  valorEmbudo: number;
  tasaConversion: number;
  reservasActivas: number;
  embudo: DashboardSeries[];
  ventasPorAsesor: DashboardSeries[];
}

export interface PortfolioDashboard {
  corte: string;
  proyectoId: number | null;
  saldoTotal: number;
  saldoVencido: number;
  recaudoMes: number;
  indiceMora: number;
  cuotasVencidas: number;
  promesasPendientes: number;
  edadesMora: DashboardSeries[];
}

export interface ServiceDashboard {
  desde: string;
  hasta: string;
  proyectoId: number | null;
  solicitudesAbiertas: number;
  solicitudesVencidas: number;
  horasPrimeraRespuesta: number;
  satisfaccionPromedio: number;
  citasPendientes: number;
  ticketsAbiertos: number;
  solicitudesPorTipo: DashboardSeries[];
}

export interface ClientDashboard {
  clienteId: number;
  codigoCliente: string;
  contratosVigentes: number;
  cuotasPendientes: number;
  saldoPendiente: number;
  documentosDisponibles: number;
  solicitudesAbiertas: number;
  proximaCita: string | null;
}

export type DashboardKind = 'gerencial' | 'comercial' | 'cartera' | 'servicio' | 'cliente';
export type DashboardPeriod = 'history' | 'month' | 'quarter' | 'year';
export type DashboardData =
  | ManagementDashboard
  | CommercialDashboard
  | PortfolioDashboard
  | ServiceDashboard
  | ClientDashboard;
