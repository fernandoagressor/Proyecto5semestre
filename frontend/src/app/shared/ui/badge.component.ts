import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type BadgeTone = 'neutral' | 'primary' | 'info' | 'success' | 'warning' | 'danger';

/**
 * Tonos de los estados que devuelve la API. Lo que no esté aquí sale en gris.
 * Se usa con `<nx-badge [status]="contrato.estado" />`, que además pone la etiqueta legible.
 */
const STATUS_TONES: Record<string, BadgeTone> = {
  // Generales
  ACTIVO: 'success', ACTIVA: 'success', HABILITADO: 'success', VIGENTE: 'success',
  INACTIVO: 'neutral', INACTIVA: 'neutral', SUSPENDIDA: 'warning', SUSPENDIDO: 'warning',
  BLOQUEADO: 'danger', BLOQUEADA: 'danger',
  PENDIENTE: 'warning', EN_PROCESO: 'info', EN_CURSO: 'info', COMPLETADA: 'success', COMPLETADO: 'success',
  CANCELADA: 'neutral', CANCELADO: 'neutral', ANULADA: 'neutral', ANULADO: 'neutral',
  APROBADA: 'success', APROBADO: 'success', RECHAZADA: 'danger', RECHAZADO: 'danger',
  // Proyectos
  PLANEACION: 'info', PAUSADO: 'warning', FINALIZADO: 'neutral',
  // Lotes
  DISPONIBLE: 'success', RESERVADO: 'warning', VENDIDO: 'info', BLOQUEADO_LOTE: 'danger', ESCRITURADO: 'primary',
  // Ventas
  BORRADOR: 'neutral', ENVIADO_FIRMA: 'info', PENDIENTE_FIRMA: 'info', NEGOCIACION: 'info', FIRMADA: 'success', PENDIENTE_PAGO: 'warning', FIRMADO: 'success', DESISTIDO: 'danger', EN_DESISTIMIENTO: 'warning',
  VENCIDA: 'danger', CONVERTIDA: 'success', LIBERADA: 'neutral',
  // Cartera
  PAGADA: 'success', PAGADO: 'success', PARCIAL: 'warning', EN_MORA: 'danger', MORA: 'danger', VENCIDO: 'danger',
  CONFIRMADO: 'success', REVERSADO: 'danger', AL_DIA: 'success',
  // CRM y servicio
  NUEVO: 'info', CONTACTADO: 'info', CALIFICADO: 'primary', DESCARTADO: 'neutral',
  GANADA: 'success', PERDIDA: 'danger', ABIERTA: 'info', ABIERTO: 'info', CERRADA: 'neutral', CERRADO: 'neutral',
  RESUELTA: 'success', RESUELTO: 'success', ESCALADA: 'danger', EN_ESPERA: 'warning',
  PROGRAMADA: 'info', CONFIRMADA: 'success', REALIZADA: 'success', NO_ASISTIO: 'danger',
  // Severidad
  CRITICA: 'danger', ALTA: 'danger', MEDIA: 'warning', BAJA: 'neutral', URGENTE: 'danger',
};

export function statusTone(status: string | null | undefined): BadgeTone {
  return (status && STATUS_TONES[status.toUpperCase()]) || 'neutral';
}

/** "EN_DESISTIMIENTO" → "En desistimiento". */
export function statusLabel(status: string | null | undefined): string {
  if (!status) return '—';
  const text = status.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * Insignia en forma de píldora.
 *
 *   <nx-badge tone="success">Al día</nx-badge>
 *   <nx-badge [status]="lote.estado" />        etiqueta y tono a partir del estado de la API
 */
@Component({
  selector: 'nx-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'classes()' },
  template: `
    @if (status()) {
      {{ label() }}
    } @else {
      <ng-content />
    }
  `,
})
export class BadgeComponent {
  readonly tone = input<BadgeTone | null>(null);
  readonly status = input<string | null | undefined>(null);
  readonly dot = input(false);
  /** Texto propio en lugar de la etiqueta derivada del estado. */
  readonly text = input<string | null>(null);

  readonly label = computed(() => this.text() ?? statusLabel(this.status()));
  readonly classes = computed(() => {
    const tone = this.tone() ?? statusTone(this.status());
    return `nx-badge nx-badge--${tone}${this.dot() ? ' nx-badge--dot' : ''}`;
  });
}
