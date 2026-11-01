export type ProspectStatus = 'NUEVO' | 'CONTACTADO' | 'CALIFICADO' | 'DESCARTADO' | 'CONVERTIDO';
export type ProspectTemperature = 'FRIO' | 'TIBIO' | 'CALIENTE';
export type ProspectSource = 'WEB' | 'REDES_SOCIALES' | 'REFERIDO' | 'PUBLICIDAD' | 'FERIA' | 'LLAMADA' | 'VISITA' | 'OTRO';
export type OpportunityStatus = 'ABIERTA' | 'GANADA' | 'PERDIDA' | 'CANCELADA';
export type OpportunityStage = 'PROSPECTO_RECIBIDO' | 'CONTACTO_INICIAL' | 'CALIFICACION' | 'PRESENTACION_PROYECTO' | 'VISITA' | 'PROPUESTA' | 'NEGOCIACION' | 'CIERRE';
export type AppointmentStatus = 'PROGRAMADA' | 'CONFIRMADA' | 'REALIZADA' | 'CANCELADA' | 'REPROGRAMADA' | 'NO_ASISTIO';
export type SimulationStatus = 'BORRADOR' | 'ENVIADA' | 'ACEPTADA' | 'VENCIDA' | 'CANCELADA';
export type Periodicity = 'MENSUAL' | 'BIMESTRAL' | 'TRIMESTRAL' | 'SEMESTRAL' | 'ANUAL';

export interface Prospect {
  id: number;
  empresaId: number;
  proyectoId: number | null;
  asesorId: number | null;
  nombres: string;
  telefono: string | null;
  correo: string | null;
  fuente: ProspectSource;
  presupuesto: number | null;
  temperatura: ProspectTemperature;
  estado: ProspectStatus;
  observaciones: string | null;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
}

export interface ProspectPayload {
  proyectoId: number | null;
  asesorId: number | null;
  nombres: string;
  telefono: string | null;
  correo: string | null;
  fuente: ProspectSource;
  presupuesto: number | null;
  temperatura: ProspectTemperature;
  observaciones: string | null;
}

export interface Opportunity {
  id: number;
  empresaId: number;
  proyectoId: number;
  codigo: string;
  prospectoId: number;
  clienteId: number | null;
  asesorId: number | null;
  etapaActual: OpportunityStage;
  estado: OpportunityStatus;
  probabilidadCierre: number;
  valorEstimado: number | null;
  fechaUltimaActividad: string | null;
  fechaProximaAccion: string | null;
  motivoPerdida: string | null;
  fechaCierre: string | null;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
}

export interface OpportunityLot {
  id: number;
  oportunidadId: number;
  loteId: number;
  prioridad: number;
  seleccionado: boolean;
}

export interface Appointment {
  id: number;
  oportunidadId: number;
  asesorId: number;
  tipo: string;
  fechaInicio: string;
  fechaFin: string;
  lugar: string | null;
  estado: AppointmentStatus;
  resultado: string | null;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
}

export interface SimulationInstallment {
  numero: number;
  fechaVencimiento: string;
  capital: number;
  interes: number;
  otros: number;
  valorTotal: number;
  saldoProyectado: number;
}

export interface Simulation {
  id: number;
  empresaId: number;
  proyectoId: number;
  codigo: string;
  oportunidadId: number;
  loteId: number;
  creadoPor: number;
  valorLote: number;
  descuento: number;
  cuotaInicial: number;
  saldoFinanciar: number;
  numeroCuotas: number;
  tasaEfectivaAnual: number;
  periodicidad: Periodicity;
  vigenteHasta: string;
  estado: SimulationStatus;
  version: number;
  creadoEn: string;
  actualizadoEn: string;
  cuotas: SimulationInstallment[];
}

export interface CreateSimulationPayload {
  oportunidadId: number;
  loteId: number;
  descuento: number;
  cuotaInicial: number;
  numeroCuotas: number;
  tasaEfectivaAnual: number;
  periodicidad: Periodicity;
  fechaPrimeraCuota: string;
  vigenteHasta: string;
}

export type ActivityType = 'LLAMADA' | 'MENSAJE' | 'CORREO' | 'VISITA' | 'NOTA' | 'PROPUESTA';

export interface CommercialActivity {
  id: number;
  oportunidadId: number;
  asesorId: number | null;
  tipo: ActivityType;
  resultado: string | null;
  descripcion: string;
  fechaActividad: string;
  proximaAccion: string | null;
  fechaProximaAccion: string | null;
  creadoEn: string;
}

export interface ActivityPayload {
  tipo: ActivityType;
  resultado: string | null;
  descripcion: string;
  fechaActividad: string;
  proximaAccion: string | null;
  fechaProximaAccion: string | null;
}

export interface StageHistory {
  id: number;
  etapaAnterior: OpportunityStage | null;
  etapaNueva: OpportunityStage;
  usuarioId: number | null;
  observacion: string | null;
  creadoEn: string;
}

export interface AppointmentPayload {
  tipo: string;
  fechaInicio: string;
  fechaFin: string;
  lugar: string | null;
}
