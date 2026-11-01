import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { AppIconComponent } from '../components/app-icon.component';

export type KpiTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger';

/**
 * Indicador numérico: etiqueta, valor grande y una línea de apoyo opcional. Con `trend` muestra
 * la variación con flecha (positiva en verde, negativa en rojo, salvo `invertTrend`).
 *
 *   <nx-kpi label="Recaudo del mes" value="$ 412 M" hint="Meta $ 480 M" [progress]="86" />
 */
@Component({
  selector: 'nx-kpi',
  imports: [AppIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'nx-card', '[attr.data-tone]': 'tone()' },
  template: `
    <span class="label">
      @if (icon()) {
        <app-icon [name]="icon()" />
      }
      {{ label() }}
    </span>
    @if (loading()) {
      <span class="nx-skeleton value-skeleton"></span>
    } @else {
      <strong class="value nx-num">{{ value() }}</strong>
    }
    @if (progress() !== null) {
      <span class="nx-progress" role="progressbar" [attr.aria-valuenow]="clampedProgress()" aria-valuemin="0" aria-valuemax="100">
        <span [style.width.%]="clampedProgress()"></span>
      </span>
    }
    @if (hint() || trend() !== null) {
      <span class="foot">
        @if (trend() !== null) {
          <span class="trend" [class.good]="trendIsGood()" [class.bad]="!trendIsGood()">
            <app-icon [name]="trend()! >= 0 ? 'trend-up' : 'trend-down'" />
            {{ trendLabel() }}
          </span>
        }
        @if (hint()) {
          <span class="hint">{{ hint() }}</span>
        }
      </span>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 8px; min-width: 0; padding: 16px 18px; }
    .label { display: flex; align-items: center; gap: 6px; color: var(--text-muted); font-size: 13px; font-weight: 600; }
    .label app-icon { width: 16px; height: 16px; color: var(--accent, var(--text-subtle)); }
    .value { overflow: hidden; color: var(--text-strong); font-family: var(--font-display); font-size: 26px; font-weight: 800; line-height: 1.15; letter-spacing: -.02em; text-overflow: ellipsis; white-space: nowrap; }
    .value-skeleton { width: 60%; height: 30px; }
    @media (max-width: 560px) { :host { padding: 12px 14px; } .value { font-size: 21px; } .label { font-size: 12px; } }
    .foot { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 10px; font-size: 12px; }
    .hint { color: var(--text-muted); }
    .trend { display: inline-flex; align-items: center; gap: 3px; font-weight: 700; }
    .trend app-icon { width: 14px; height: 14px; }
    .good { color: var(--tone-success-fg); }
    .bad { color: var(--tone-danger-fg); }
    :host([data-tone='primary']) { --accent: var(--color-primary); }
    :host([data-tone='success']) { --accent: var(--tone-success-fg); }
    :host([data-tone='warning']) { --accent: var(--tone-warning-fg); }
    :host([data-tone='danger']) { --accent: var(--tone-danger-fg); }
    :host([data-tone='danger']) .value, :host([data-tone='warning']) .value { color: var(--accent); }
    :host([data-tone='success']) .nx-progress > span { background: #10a37f; }
    :host([data-tone='warning']) .nx-progress > span { background: #d97706; }
    :host([data-tone='danger']) .nx-progress > span { background: #d92d20; }
  `,
})
export class KpiCardComponent {
  readonly label = input.required<string>();
  readonly value = input<string | number>('—');
  readonly hint = input<string>('');
  readonly icon = input<string>('');
  readonly tone = input<KpiTone>('neutral');
  readonly loading = input(false);
  /** Porcentaje 0–100 para la barra; null la oculta. */
  readonly progress = input<number | null>(null);
  /** Variación en puntos porcentuales; null la oculta. */
  readonly trend = input<number | null>(null);
  /** Para indicadores donde subir es malo (mora, tickets vencidos). */
  readonly invertTrend = input(false);

  readonly clampedProgress = computed(() => Math.max(0, Math.min(100, Math.round(this.progress() ?? 0))));
  readonly trendIsGood = computed(() => ((this.trend() ?? 0) >= 0) !== this.invertTrend());
  readonly trendLabel = computed(() => {
    const t = this.trend() ?? 0;
    return `${t > 0 ? '+' : ''}${t.toLocaleString('es-CO', { maximumFractionDigits: 1 })} %`;
  });
}
