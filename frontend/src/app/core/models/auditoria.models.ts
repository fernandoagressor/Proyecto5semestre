// Auditoría: bitácora de eventos, alertas de anomalías y exportaciones.
// Espejo de dto/gerencia/AuditoriaDtos.java.

export interface AuditEvent {
  id: number;
  empresaId: number;
  proyectoId: number | null;
  actorId: number | null;
  modulo: string;
  accion: string;
  entidadTipo: string | null;
  entidadId: number | null;
  descripcion: string | null;
  datosAnterioresJson: string | null;
  datosNuevosJson: string | null;
  ipOrigen: string | null;
  agenteUsuario: string | null;
  correlationId: string | null;
  ocurridoEn: string;
}

export type AlertStatus = 'ABIERTA' | 'EN_INVESTIGACION' | 'RESUELTA' | 'DESCARTADA';
export type AlertSeverity = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA';

export interface AnomalyAlert {
  id: number;
  tipo: string;
  severidad: AlertSeverity;
  usuarioId: number | null;
  eventoAuditoriaId: number | null;
  descripcion: string;
  estado: AlertStatus;
  asignadoA: number | null;
  resueltaEn: string | null;
  resolucion: string | null;
  creadoEn: string;
  version: number;
}

export interface AuditExport {
  id: number;
  formato: string;
  motivo: string;
  estado: string;
  archivoId: number | null;
  expiraEn: string | null;
  creadoEn: string;
}

export interface AuditEventQuery {
  proyectoId?: number | null;
  actorId?: number | null;
  modulo?: string;
  accion?: string;
  /** ISO local, AAAA-MM-DDTHH:mm:ss */
  desde?: string;
  hasta?: string;
  pagina?: number;
  tamano?: number;
}
