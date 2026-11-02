export type ServiceRequestType = 'PETICION' | 'QUEJA' | 'RECLAMO' | 'SUGERENCIA' | 'SOLICITUD';
export type ServicePriority = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA';
export type ServiceRequestStatus =
  | 'ABIERTA'
  | 'EN_PROCESO'
  | 'PENDIENTE_CLIENTE'
  | 'PENDIENTE_TERCERO'
  | 'RESUELTA'
  | 'CERRADA'
  | 'REABIERTA'
  | 'CANCELADA';
export type ServiceAppointmentStatus =
  | 'PROGRAMADA'
  | 'CONFIRMADA'
  | 'REALIZADA'
  | 'CANCELADA'
  | 'REPROGRAMADA';

export interface ServiceRequest {
  id: number;
  codigo: string;
  proyectoId: number | null;
  clienteId: number;
  contratoId: number | null;
  asignadoA: number | null;
  tipo: ServiceRequestType;
  categoria: string;
  asunto: string;
  descripcion: string;
  prioridad: ServicePriority;
  estado: ServiceRequestStatus;
  fechaLimite: string;
  fechaPrimeraRespuesta: string | null;
  fechaResolucion: string | null;
  fechaCierre: string | null;
  creadoEn: string;
  actualizadoEn: string;
}

export interface CreateServiceRequestPayload {
  proyectoId: number | null;
  clienteId: number | null;
  contratoId: number | null;
  tipo: ServiceRequestType;
  categoria: string;
  asunto: string;
  descripcion: string;
  prioridad: ServicePriority;
}

export interface ServiceMessage {
  id: number;
  usuarioId: number | null;
  mensaje: string;
  interno: boolean;
  canal: string;
  creadoEn: string;
}

export interface ServiceAttachment {
  id: number;
  mensajeId: number | null;
  archivoId: number;
  nombreArchivo: string | null;
  mimeType: string | null;
  tamanoBytes: number | null;
  creadoEn: string;
}

export interface ServiceHistory {
  id: number;
  campo: string;
  valorAnterior: string | null;
  valorNuevo: string | null;
  usuarioId: number | null;
  motivo: string | null;
  creadoEn: string;
}

export interface ServiceAppointment {
  id: number;
  proyectoId: number | null;
  clienteId: number;
  solicitudId: number | null;
  responsableId: number | null;
  tipo: string;
  fechaInicio: string;
  fechaFin: string;
  lugar: string | null;
  estado: ServiceAppointmentStatus;
  resultado: string | null;
  creadoEn: string;
}

export interface CreateServiceAppointmentPayload {
  proyectoId: number | null;
  clienteId: number | null;
  solicitudId: number | null;
  responsableId: number | null;
  tipo: string;
  fechaInicio: string;
  fechaFin: string;
  lugar: string | null;
}

export interface ClientPortalSummary {
  clienteId: number;
  codigoCliente: string;
  contratosVigentes: number;
  cuotasPendientes: number;
  saldoPendiente: number;
  documentosDisponibles: number;
  solicitudesAbiertas: number;
  proximaCita: string | null;
}

export interface ClientPortalDocument {
  id: number;
  codigo: string;
  tipoDocumento: string;
  nombre: string;
  proyectoId: number | null;
  entidadTipo: string | null;
  entidadId: number | null;
  numeroVersion: number;
  archivoId: number;
  nombreArchivo: string;
  mimeType: string;
  tamanoBytes: number;
  sha256: string;
  actualizadoEn: string;
}

export interface ClientDataChange {
  id: number;
  clienteId: number;
  solicitadoPor: number;
  campo: string;
  valorAnterior: string | null;
  valorSolicitado: string;
  motivo: string;
  estado: 'PENDIENTE' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA';
  revisadoPor: number | null;
  revisadoEn: string | null;
  comentarioRevision: string | null;
  creadoEn: string;
}

/** Contrato del cliente en el portal, con avance de pago y próxima cuota. */
export interface PortalContract {
  id: number;
  codigo: string;
  estado: string;
  proyecto: string;
  ciudad: string;
  lote: string;
  areaM2: number;
  valorTotal: number;
  pagado: number;
  saldoPendiente: number;
  saldoVencido: number;
  cuotasPagadas: number;
  cuotasTotales: number;
  proximaCuotaFecha: string | null;
  proximaCuotaValor: number | null;
  fechaFirma: string | null;
}

export interface PortalInstallment {
  id: number;
  numero: number;
  fechaVencimiento: string;
  valorProgramado: number;
  valorPagado: number;
  saldo: number;
  interesMora: number;
  diasMora: number;
  estado: string;
}

export interface PortalPayment {
  id: number;
  codigo: string;
  contrato: string | null;
  fechaPago: string;
  valor: number;
  metodo: string;
  referencia: string | null;
  estado: string;
}
