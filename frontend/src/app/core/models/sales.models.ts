export type CustomerStatus = 'ACTIVO' | 'INACTIVO' | 'PENDIENTE';
export type ReservationStatus = 'PENDIENTE_PAGO' | 'ACTIVA' | 'VENCIDA' | 'CANCELADA' | 'CONVERTIDA';
export type ReservationPaymentStatus = 'PENDIENTE' | 'CONFIRMADO' | 'RECHAZADO';
export type ContractStatus = 'BORRADOR' | 'NEGOCIACION' | 'PENDIENTE_FIRMA' | 'VIGENTE' | 'FINALIZADO' | 'ANULADO';
export type ContractType = 'PROMESA_COMPRAVENTA' | 'COMPRAVENTA' | 'CESION';
export type ContractCustomerRole = 'PRINCIPAL' | 'COPROPIETARIO' | 'BENEFICIARIO' | 'APODERADO';
export type SignerType = 'COMPRADOR' | 'COPROPIETARIO' | 'REPRESENTANTE' | 'VENDEDOR' | 'TESTIGO';
export type SignerStatus = 'PENDIENTE' | 'ENVIADA' | 'FIRMADA' | 'RECHAZADA';
export type SignatureMethod = 'MANUSCRITA' | 'ELECTRONICA' | 'DIGITAL';
export type SignatureValidation = 'VALIDA' | 'INVALIDA' | 'PENDIENTE';

export interface Customer {
  id: number;
  empresaId: number;
  personaId: number;
  usuarioId: number | null;
  codigoCliente: string;
  tipoPersona: 'NATURAL' | 'JURIDICA';
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string | null;
  nombreCompleto: string;
  telefono: string | null;
  correo: string | null;
  direccion: string | null;
  origen: string | null;
  observaciones: string | null;
  estado: CustomerStatus;
  fechaAlta: string;
  creadoEn: string;
  actualizadoEn: string;
}

export interface CustomerPayload {
  tipoPersona: 'NATURAL' | 'JURIDICA';
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidos: string | null;
  telefono: string | null;
  correo: string | null;
  direccion: string | null;
  origen: string | null;
  observaciones: string | null;
}

export interface ReservationHistory {
  id: number;
  estadoAnterior: ReservationStatus | null;
  estadoNuevo: ReservationStatus;
  motivo: string;
  usuarioId: number | null;
  creadoEn: string;
}

export interface Reservation {
  id: number;
  empresaId: number;
  proyectoId: number;
  codigo: string;
  oportunidadId: number;
  simulacionId: number | null;
  clienteId: number;
  loteId: number;
  asesorId: number | null;
  fechaReserva: string;
  fechaVencimiento: string;
  valorReserva: number;
  formaPago: string;
  estadoPago: ReservationPaymentStatus;
  estado: ReservationStatus;
  motivoCancelacion: string | null;
  comprobanteArchivoId: number | null;
  pagoConfirmadoEn: string | null;
  pagoConfirmadoPor: number | null;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
  historial: ReservationHistory[];
}

export interface CreateReservationPayload {
  oportunidadId: number;
  simulacionId: number | null;
  clienteId: number;
  loteId: number;
  fechaVencimiento: string;
  valorReserva: number;
  formaPago: string;
}

export interface ContractCustomer {
  id: number;
  clienteId: number;
  rol: ContractCustomerRole;
  porcentajeParticipacion: number;
}

export interface SignatureEvent {
  id: number;
  metodo: SignatureMethod;
  proveedor: string | null;
  referenciaExterna: string | null;
  hashDocumento: string;
  ipOrigen: string | null;
  firmadaEn: string;
  estadoValidacion: SignatureValidation;
}

export interface ContractSigner {
  id: number;
  personaId: number;
  tipoFirmante: SignerType;
  ordenFirma: number;
  estado: SignerStatus;
  fechaProgramada: string | null;
  fechaFirma: string | null;
  archivoFirmaId: number | null;
  version: number;
  eventos: SignatureEvent[];
}

export interface ContractVersion {
  id: number;
  numeroVersion: number;
  archivoId: number | null;
  datos: Record<string, unknown>;
  motivo: string;
  creadoPor: number;
  vigente: boolean;
  creadoEn: string;
}

export interface Contract {
  id: number;
  empresaId: number;
  proyectoId: number;
  codigo: string;
  oportunidadId: number;
  reservaId: number;
  clientePrincipalId: number;
  loteId: number;
  asesorId: number | null;
  tipoContrato: ContractType;
  valorLote: number;
  otrosConceptos: number;
  descuento: number;
  valorTotal: number;
  cuotaInicial: number;
  saldoFinanciado: number;
  numeroCuotas: number;
  fechaFirma: string | null;
  fechaInicio: string;
  fechaFinalizacion: string | null;
  estado: ContractStatus;
  motivoAnulacion: string | null;
  anuladoEn: string | null;
  anuladoPor: number | null;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
  clientes: ContractCustomer[];
  versiones: ContractVersion[];
  firmantes: ContractSigner[];
}

export interface ContractConditionsPayload {
  tipoContrato: ContractType;
  otrosConceptos: number;
  descuento: number;
  cuotaInicial: number;
  numeroCuotas: number;
  fechaInicio: string;
  fechaFinalizacion: string | null;
}

export interface SalesDocument {
  id: number;
  empresaId: number;
  proyectoId: number | null;
  nombreOriginal: string;
  mimeType: string;
  tamanoBytes: number;
  sha256: string;
  clasificacion: string;
  creadoEn: string;
}
