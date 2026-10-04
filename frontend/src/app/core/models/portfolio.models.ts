export type InstallmentStatus = 'PENDIENTE' | 'PARCIAL' | 'PAGADA' | 'VENCIDA' | 'CONDONADA' | 'CANCELADA';
export type PaymentStatus = 'PENDIENTE' | 'EN_REVISION' | 'CONFIRMADO' | 'RECHAZADO' | 'REVERSADO';
export type PaymentConcept = 'RESERVA' | 'INICIAL' | 'CAPITAL' | 'INTERES' | 'MORA' | 'OTRO';
export type PaymentFrequency = 'MENSUAL' | 'BIMESTRAL' | 'TRIMESTRAL' | 'SEMESTRAL' | 'ANUAL';
export type CollectionChannel = 'LLAMADA' | 'SMS' | 'EMAIL' | 'WHATSAPP' | 'VISITA';

export interface PortfolioSummary {
  contratosVigentes: number;
  planesActivos: number;
  contratosSinPlan: number;
  carteraTotal: number;
  totalRecaudado: number;
  saldoPendiente: number;
  carteraVencida: number;
  porcentajeMora: number;
  cuotasProximas: number;
  pagosPendientes: number;
}

export interface PortfolioContract {
  contratoId: number;
  contratoCodigo: string;
  proyectoId: number;
  proyectoNombre: string;
  clienteId: number;
  clienteNombre: string;
  clienteDocumento: string;
  loteId: number;
  loteCodigo: string;
  valorContrato: number;
  cuotaInicial: number;
  capitalFinanciado: number;
  planPagoId: number | null;
  estadoPlan: string | null;
  numeroCuotas: number;
  totalProgramado: number;
  totalPagado: number;
  saldoPendiente: number;
  saldoVencido: number;
  proximoVencimiento: string | null;
  cuotasPendientes: number;
  cuotasVencidas: number;
}

export interface Installment {
  id: number;
  numero: number;
  fechaVencimiento: string;
  capital: number;
  interes: number;
  otrosConceptos: number;
  valorProgramado: number;
  valorPagado: number;
  saldo: number;
  diasMora: number;
  interesMora: number;
  moraCalculadaHasta: string | null;
  estado: InstallmentStatus;
  version: number;
}

export interface PaymentPlan {
  id: number;
  contratoId: number;
  versionPlan: number;
  capitalFinanciado: number;
  tasaEfectivaAnual: number;
  tasaMoraEfectivaAnual: number;
  numeroCuotas: number;
  periodicidad: PaymentFrequency;
  fechaPrimeraCuota: string;
  estado: 'BORRADOR' | 'ACTIVO' | 'REEMPLAZADO' | 'CANCELADO';
  origen: 'CONTRATO' | 'REFINANCIACION';
  version: number;
  cuotas: Installment[];
}

export interface PaymentApplication {
  id: number;
  cuotaId: number | null;
  facturaId: number | null;
  concepto: PaymentConcept;
  valor: number;
}

export interface PortfolioPayment {
  id: number;
  codigo: string;
  contratoId: number | null;
  reservaId: number | null;
  clienteId: number;
  metodoPagoId: number;
  fechaPago: string;
  valor: number;
  referencia: string | null;
  comprobanteId: number | null;
  origen: string;
  estado: PaymentStatus;
  registradoPor: number | null;
  validadoPor: number | null;
  validadoEn: string | null;
  observaciones: string | null;
  version: number;
  aplicaciones: PaymentApplication[];
}

export interface PaymentMethod {
  id: number;
  codigo: string;
  nombre: string;
  requiereReferencia: boolean;
  requiereComprobante: boolean;
}

export interface OverdueInstallment {
  cuotaId: number;
  contratoId: number;
  planPagoId: number;
  numeroCuota: number;
  fechaVencimiento: string;
  diasMora: number;
  saldo: number;
  interesMora: number;
  moraCalculadaHasta: string | null;
  totalVencido: number;
}

export interface FollowUpInstallment {
  cuotaId: number;
  contratoId: number;
  planPagoId: number;
  numeroCuota: number;
  fechaVencimiento: string;
  estado: Extract<InstallmentStatus, 'PENDIENTE' | 'PARCIAL' | 'VENCIDA'>;
  diasMora: number;
  diasParaVencimiento: number;
  saldo: number;
  interesMora: number;
  moraCalculadaHasta: string | null;
  totalPendiente: number;
}

export interface CollectionManagement {
  id: number;
  contratoId: number;
  cuotaId: number | null;
  gestorId: number;
  canal: CollectionChannel;
  resultado: string;
  detalle: string | null;
  promesaPagoFecha: string | null;
  promesaPagoValor: number | null;
  fechaGestion: string;
}

export interface PortfolioConfiguration {
  id: number;
  empresaId: number;
  tasaEfectivaAnual: number;
  tasaMoraEfectivaAnual: number;
  periodicidad: PaymentFrequency;
  diasPrimeraCuota: number;
  autoGenerarPlan: boolean;
  version: number;
}
