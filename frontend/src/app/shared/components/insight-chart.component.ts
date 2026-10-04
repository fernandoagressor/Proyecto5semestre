import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface InsightChartPoint {
  categoria: string;
  valor: number;
  cantidad: number;
}

interface PlottedPoint {
  x: number;
  y: number;
  label: string;
  display: string;
}

@Component({
  selector: 'app-insight-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (points().length === 0) {
      <div class="chart-empty"><span></span><strong>Sin movimientos todavía</strong><small>{{ emptyMessage() }}</small></div>
    } @else if (variant() === 'donut') {
      <div class="donut-layout">
        <div class="donut" [style.background]="donutGradient()" role="img" [attr.aria-label]="accessibleSummary()">
          <div><strong>{{ formatNumber(total()) }}</strong><small>{{ totalLabel() }}</small></div>
        </div>
        <div class="legend">
          @for (point of points(); track point.categoria; let index = $index) {
            <div class="legend-row"><i [style.background]="color(index)"></i><span>{{ label(point.categoria) }}</span><strong>{{ formatPoint(point) }}</strong></div>
          }
        </div>
      </div>
    } @else if (variant() === 'line') {
      <div class="line-layout">
        <svg class="line-svg" viewBox="0 0 320 150" preserveAspectRatio="none" role="img" [attr.aria-label]="accessibleSummary()">
          <defs><linearGradient id="nexoraChartArea" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#6366f1" stop-opacity=".25" /><stop offset="100%" stop-color="#22d3b6" stop-opacity=".02" /></linearGradient></defs>
          <path class="line-area" [attr.d]="areaPath()" />
          <polyline class="line-stroke" [attr.points]="polyline()" />
          @for (point of plottedPoints(); track point.x) { <circle class="line-dot" [attr.cx]="point.x" [attr.cy]="point.y" r="4" /> }
        </svg>
        <div class="line-labels">
          @for (point of plottedPoints(); track point.x) { <div><small>{{ point.label }}</small><strong>{{ point.display }}</strong></div> }
        </div>
      </div>
    } @else {
      <div class="bars-layout">
        @for (point of points(); track point.categoria; let index = $index) {
          <div class="bar-row"><div><span>{{ label(point.categoria) }}</span><strong>{{ formatPoint(point) }}</strong></div><span class="bar-track"><i [style.width.%]="width(point)" [style.background]="color(index)"></i></span>@if (point.cantidad > 0 && format() === 'currency') { <small>{{ formatNumber(point.cantidad) }} registros</small> }</div>
        }
      </div>
    }
  `,
  styles: `
    :host { display:block; min-width:0; }
    .chart-empty { min-height:10rem; display:grid; align-content:center; justify-items:center; gap:.4rem; text-align:center; }
    .chart-empty > span { width:2.7rem; height:2.7rem; border:1px dashed var(--border-medium); border-radius:50%; background:radial-gradient(circle,var(--color-primary-soft),transparent 70%); }
    .chart-empty strong { color:var(--text-strong); font-size:.73rem; }.chart-empty small { max-width:16rem; color:var(--text-subtle); font-size:.6rem; }
    .donut-layout { display:grid; grid-template-columns:minmax(7rem, .78fr) minmax(8rem,1fr); align-items:center; gap:1rem; }
    .donut { position:relative; width:min(100%,10rem); aspect-ratio:1; margin-inline:auto; border-radius:50%; }
    .donut::before { position:absolute; inset:16%; border-radius:50%; background:var(--surface-card); content:''; }
    .donut > div { position:absolute; inset:0; display:grid; align-content:center; justify-items:center; gap:.1rem; }
    .donut strong { color:var(--text-strong); font-size:1.45rem; letter-spacing:-.06em; }.donut small { color:var(--text-subtle); font-size:.52rem; }
    .legend { display:grid; gap:.6rem; }.legend-row { display:grid; grid-template-columns:.45rem minmax(0,1fr) auto; align-items:center; gap:.4rem; }
    .legend-row i { width:.45rem; height:.45rem; border-radius:50%; }.legend-row span { overflow:hidden; color:var(--text-muted); font-size:.58rem; text-overflow:ellipsis; white-space:nowrap; }.legend-row strong { color:var(--text-strong); font-size:.58rem; }
    .line-layout { display:grid; gap:.7rem; }.line-svg { display:block; width:100%; height:9.5rem; overflow:visible; }.line-area { fill:url(#nexoraChartArea); }.line-stroke { fill:none; stroke:#6366f1; stroke-width:3; stroke-linecap:round; stroke-linejoin:round; }.line-dot { fill:var(--surface-card); stroke:#6366f1; stroke-width:2.5; }
    .line-labels { display:grid; grid-auto-columns:minmax(0,1fr); grid-auto-flow:column; gap:.45rem; }.line-labels div { display:grid; gap:.18rem; min-width:0; }.line-labels small,.line-labels strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.line-labels small { color:var(--text-subtle); font-size:.52rem; }.line-labels strong { color:var(--text-strong); font-size:.56rem; }
    .bars-layout { display:grid; gap:.82rem; }.bar-row { display:grid; gap:.36rem; }.bar-row > div { display:flex; justify-content:space-between; gap:.65rem; }.bar-row > div span { color:var(--text-muted); font-size:.62rem; }.bar-row > div strong { color:var(--text-strong); font-size:.63rem; }.bar-track { overflow:hidden; height:.5rem; border-radius:2rem; background:var(--surface-muted); }.bar-track i { display:block; height:100%; min-width:.28rem; border-radius:inherit; transition:width .45s cubic-bezier(.2,.8,.2,1); }.bar-row > small { color:var(--text-subtle); font-size:.49rem; }
    @media(max-width:480px) { .donut-layout { grid-template-columns:1fr; }.donut { max-width:8rem; } }
  `,
})
export class InsightChartComponent {
  readonly points = input.required<readonly InsightChartPoint[]>();
  readonly variant = input<'bars' | 'donut' | 'line'>('bars');
  readonly format = input<'currency' | 'number'>('number');
  readonly totalLabel = input('registros');
  readonly emptyMessage = input('Los datos aparecerán cuando exista actividad registrada.');

  private readonly palette = ['#6366f1', '#22d3b6', '#38bdf8', '#f59e0b', '#f472b6', '#8b5cf6', '#ef4444'];

  readonly total = computed(() => this.points().reduce((sum, point) => sum + this.value(point), 0));

  readonly donutGradient = computed(() => {
    const total = this.total();
    if (total <= 0) return 'var(--surface-muted)';
    let accumulated = 0;
    const segments = this.points().map((point, index) => {
      const from = accumulated;
      accumulated += this.value(point) / total * 100;
      return `${this.color(index)} ${from}% ${accumulated}%`;
    });
    return `conic-gradient(${segments.join(', ')})`;
  });

  readonly plottedPoints = computed<PlottedPoint[]>(() => {
    const values = this.points();
    const maximum = Math.max(...values.map((point) => this.value(point)), 1);
    return values.map((point, index) => ({
      x: values.length === 1 ? 160 : 12 + (index / (values.length - 1)) * 296,
      y: 132 - (this.value(point) / maximum) * 110,
      label: this.label(point.categoria),
      display: this.formatPoint(point),
    }));
  });

  readonly polyline = computed(() => this.plottedPoints().map((point) => `${point.x},${point.y}`).join(' '));

  readonly areaPath = computed(() => {
    const points = this.plottedPoints();
    if (points.length === 0) return '';
    return `M ${points[0].x} 140 L ${points.map((point) => `${point.x} ${point.y}`).join(' L ')} L ${points[points.length - 1].x} 140 Z`;
  });

  readonly accessibleSummary = computed(() => this.points()
    .map((point) => `${this.label(point.categoria)}: ${this.formatPoint(point)}`).join(', '));

  color(index: number): string {
    return this.palette[index % this.palette.length];
  }

  width(point: InsightChartPoint): number {
    const maximum = Math.max(...this.points().map((item) => this.value(item)), 1);
    return Math.min(100, Math.max(this.value(point) > 0 ? 4 : 0, this.value(point) / maximum * 100));
  }

  label(category: string): string {
    if (/^\d{4}-\d{2}$/.test(category)) {
      const [year, month] = category.split('-').map(Number);
      return new Intl.DateTimeFormat('es-CO', { month: 'short', year: '2-digit' })
        .format(new Date(year, month - 1, 1)).replace('.', '');
    }
    const value = category.replaceAll('_', ' ').toLowerCase();
    return value.charAt(0).toUpperCase() + value.slice(1);
  }

  formatPoint(point: InsightChartPoint): string {
    const value = this.value(point);
    if (this.format() === 'currency') {
      return new Intl.NumberFormat('es-CO', {
        style: 'currency', currency: 'COP', maximumFractionDigits: 0,
        notation: Math.abs(value) >= 1_000_000_000 ? 'compact' : 'standard',
      }).format(value);
    }
    return this.formatNumber(value);
  }

  formatNumber(value: number): string {
    return new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 }).format(value);
  }

  private value(point: InsightChartPoint): number {
    return Math.max(0, Number(this.format() === 'currency' ? point.valor : point.cantidad || point.valor || 0));
  }
}
