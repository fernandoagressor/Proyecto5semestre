import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { Project } from '../../core/models/empresa.models';
import { Profitability } from '../../core/models/gerencia.models';
import { GerenciaService } from '../../core/services/gerencia.service';
import { apiErrorMessage, formatCop, formatCopCompact, formatPercent, NX_UI, todayIso } from '../../shared/ui';

@Component({
  selector: 'app-profitability-tab',
  imports: [...NX_UI],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './management.shared.scss',
  template: `
    <div class="tab-body">
      <div class="nx-toolbar">
        <label class="nx-field"><span>Desde</span><input class="nx-input" type="date" [value]="from()" (change)="from.set($any($event.target).value)" /></label>
        <label class="nx-field"><span>Hasta</span><input class="nx-input" type="date" [value]="to()" (change)="to.set($any($event.target).value)" /></label>
        <button type="button" class="nx-btn nx-btn--ghost" (click)="load()" [disabled]="loading()"><app-icon name="refresh" /> Actualizar</button>
      </div>

      <nx-state [loading]="loading()" [error]="error()" [empty]="!data()" (retry)="load()" emptyTitle="Sin datos para el rango">
        @if (view(); as v) {
          <section class="nx-grid nx-grid--kpi" aria-label="Resultado del periodo">
            <nx-kpi label="Ventas" [value]="v.sales" icon="sales" tone="primary" hint="Contratos del periodo" />
            <nx-kpi label="Recaudo" [value]="v.collected" icon="money" [hint]="v.collectedHint" />
            <nx-kpi label="Costos aprobados" [value]="v.costs" icon="wallet" [hint]="v.deviationHint" [tone]="v.overBudget ? 'danger' : 'neutral'" />
            <nx-kpi label="Utilidad" [value]="v.profit" icon="trend-up" [tone]="v.profitPositive ? 'success' : 'danger'" />
            <nx-kpi label="Margen" [value]="v.margin" icon="percent" [tone]="v.profitPositive ? 'success' : 'danger'" />
          </section>

          <section class="nx-card nx-card--pad nx-card--stack" aria-label="Comparativo">
            <h2 class="nx-section-title">Comparativo del periodo</h2>
            <div class="compare">
              @for (row of v.bars; track row.label) {
                <div class="compare-row">
                  <span>{{ row.label }}</span>
                  <div class="compare-bar"><span [style.width.%]="row.width" [style.--bar]="row.color"></span></div>
                  <b>{{ row.value }}</b>
                </div>
              }
            </div>
            <p class="nx-section-subtitle">Utilidad = ventas − costos aprobados. Margen = utilidad sobre ventas.</p>
          </section>
        }
      </nx-state>
    </div>
  `,
})
export class ProfitabilityTabComponent {
  private readonly api = inject(GerenciaService);

  readonly projectId = input<number | null>(null);
  readonly projects = input<Project[]>([]);

  readonly from = signal(`${new Date().getFullYear()}-01-01`);
  readonly to = signal(todayIso());
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly data = signal<Profitability | null>(null);

  readonly view = computed(() => {
    const d = this.data();
    if (!d) return null;
    const n = (x: number | null | undefined) => Number(x ?? 0);
    const max = Math.max(n(d.ventas), n(d.recaudo), n(d.presupuestoAprobado), n(d.costosAprobados), 1);
    const bar = (label: string, value: number, color: string) => ({ label, value: formatCop(value), width: (Math.max(value, 0) / max) * 100, color });
    const deviation = n(d.desviacionPresupuesto);
    return {
      sales: formatCopCompact(d.ventas),
      collected: formatCopCompact(d.recaudo),
      collectedHint: n(d.ventas) > 0 ? `${formatPercent((n(d.recaudo) / n(d.ventas)) * 100)} de las ventas` : '',
      costs: formatCopCompact(d.costosAprobados),
      deviationHint: n(d.presupuestoAprobado) > 0 ? `Desviación ${formatCopCompact(deviation)} frente al presupuesto` : 'Sin presupuesto aprobado',
      overBudget: n(d.presupuestoAprobado) > 0 && n(d.costosAprobados) > n(d.presupuestoAprobado),
      profit: formatCopCompact(d.utilidad),
      profitPositive: n(d.utilidad) >= 0,
      margin: d.margen === null ? '—' : formatPercent(d.margen),
      bars: [
        bar('Ventas', n(d.ventas), '#4f46e5'),
        bar('Recaudo', n(d.recaudo), '#10a37f'),
        bar('Presupuesto', n(d.presupuestoAprobado), '#8a96a9'),
        bar('Costos', n(d.costosAprobados), n(d.costosAprobados) > n(d.presupuestoAprobado) && n(d.presupuestoAprobado) > 0 ? '#d92d20' : '#d97706'),
      ],
    };
  });

  constructor() {
    effect(() => {
      const project = this.projectId();
      const from = this.from();
      const to = this.to();
      untracked(() => this.load(project, from, to));
    });
  }

  load(project = this.projectId(), from = this.from(), to = this.to()): void {
    if (!from || !to) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.profitability({ proyectoId: project, desde: from, hasta: to }).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudo calcular la rentabilidad.'));
        this.loading.set(false);
      },
    });
  }
}
