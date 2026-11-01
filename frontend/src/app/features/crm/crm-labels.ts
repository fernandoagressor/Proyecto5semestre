import { ActivityType, OpportunityStage, ProspectSource, ProspectStatus, ProspectTemperature } from '../../core/models/crm.models';
import { BadgeTone } from '../../shared/ui';

export interface StageDefinition {
  value: OpportunityStage;
  label: string;
  probability: number;
  color: string;
}

/** Etapas del embudo en orden, con la probabilidad sugerida al llegar a cada una. */
export const STAGES: readonly StageDefinition[] = [
  { value: 'PROSPECTO_RECIBIDO', label: 'Recibido', probability: 5, color: '#8a96a9' },
  { value: 'CONTACTO_INICIAL', label: 'Contacto', probability: 10, color: '#6b7a90' },
  { value: 'CALIFICACION', label: 'Calificación', probability: 25, color: '#0ea5e9' },
  { value: 'PRESENTACION_PROYECTO', label: 'Presentación', probability: 40, color: '#6366f1' },
  { value: 'VISITA', label: 'Visita', probability: 55, color: '#4f46e5' },
  { value: 'PROPUESTA', label: 'Propuesta', probability: 70, color: '#d97706' },
  { value: 'NEGOCIACION', label: 'Negociación', probability: 85, color: '#ea580c' },
  { value: 'CIERRE', label: 'Cierre', probability: 95, color: '#10a37f' },
];

export function stageOf(value: OpportunityStage | null | undefined): StageDefinition | undefined {
  return STAGES.find((s) => s.value === value);
}

export function nextStage(value: OpportunityStage): StageDefinition | undefined {
  const i = STAGES.findIndex((s) => s.value === value);
  return i >= 0 ? STAGES[i + 1] : undefined;
}

export const SOURCES: readonly { value: ProspectSource; label: string }[] = [
  { value: 'WEB', label: 'Sitio web' },
  { value: 'REDES_SOCIALES', label: 'Redes sociales' },
  { value: 'REFERIDO', label: 'Referido' },
  { value: 'PUBLICIDAD', label: 'Publicidad' },
  { value: 'FERIA', label: 'Feria' },
  { value: 'LLAMADA', label: 'Llamada' },
  { value: 'VISITA', label: 'Visita a sala' },
  { value: 'OTRO', label: 'Otro' },
];

export const TEMPERATURES: readonly { value: ProspectTemperature; label: string; tone: BadgeTone }[] = [
  { value: 'CALIENTE', label: 'Caliente', tone: 'danger' },
  { value: 'TIBIO', label: 'Tibio', tone: 'warning' },
  { value: 'FRIO', label: 'Frío', tone: 'info' },
];

export const PROSPECT_STATUSES: readonly { value: ProspectStatus; label: string }[] = [
  { value: 'NUEVO', label: 'Nuevo' },
  { value: 'CONTACTADO', label: 'Contactado' },
  { value: 'CALIFICADO', label: 'Calificado' },
  { value: 'DESCARTADO', label: 'Descartado' },
  { value: 'CONVERTIDO', label: 'Convertido' },
];

export const ACTIVITY_TYPES: readonly { value: ActivityType; label: string; icon: string }[] = [
  { value: 'LLAMADA', label: 'Llamada', icon: 'phone' },
  { value: 'MENSAJE', label: 'Mensaje', icon: 'send' },
  { value: 'CORREO', label: 'Correo', icon: 'mail' },
  { value: 'VISITA', label: 'Visita', icon: 'location' },
  { value: 'PROPUESTA', label: 'Propuesta', icon: 'file' },
  { value: 'NOTA', label: 'Nota', icon: 'edit' },
];

export const APPOINTMENT_TYPES = ['VISITA', 'LLAMADA', 'REUNION', 'VIDEOLLAMADA', 'FIRMA'];

export function sourceLabel(value: ProspectSource | null | undefined): string {
  return SOURCES.find((s) => s.value === value)?.label ?? '—';
}

export function temperatureOf(value: ProspectTemperature | null | undefined) {
  return TEMPERATURES.find((t) => t.value === value);
}

export function activityOf(value: ActivityType) {
  return ACTIVITY_TYPES.find((a) => a.value === value) ?? ACTIVITY_TYPES[ACTIVITY_TYPES.length - 1];
}

/** "2026-11-04T09:30" para <input type="datetime-local"> a partir de una fecha local. */
export function toLocalInput(date: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}`;
}

/** Valor de <input type="datetime-local"> a LocalDateTime de la API (agrega los segundos). */
export function fromLocalInput(value: string | null | undefined): string | null {
  if (!value) return null;
  return value.length === 16 ? `${value}:00` : value;
}
