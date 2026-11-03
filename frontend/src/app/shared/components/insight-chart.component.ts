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
          <div class="bar-row"><div><span>{{ label(point.categoria) }}</span><strong>{{ formatPoint(point) }}</strong></div><span class="bar-track"><i [style.width.%]="width(point)"></i></span>@if (point.cantidad > 0 && format() === 'currency') { <small>{{ formatNumber(point.cantidad) }} registros</small> }</div>
        }
      </div>
    }
  `,
  styles: `
    :host { display:block; min-width:0; }
    .chart-empty { min-height:10rem; display:grid; align-content:center; justify-items:center; gap:.4rem; text-align:center; }
    .chart-empty > span { width:2.7rem; height:2.7rem; border:1px dashed var(--border-medium); border-radius:50%; background:var(--color-primary-soft); }
    .chart-empty strong { color:var(--text-strong); font-size:15px; }.chart-empty small { max-width:16rem; color:var(--text-subtle); font-size:13px; }
    .donut-layout { display:grid; grid-template-columns:minmax(7rem, .78fr) minmax(8rem,1fr); align-items:center; gap:1rem; }
    .donut { position:relative; width:min(100%,10rem); aspect-ratio:1; margin-inline:auto; border-radius:50%; }
    .donut::before { position:absolute; inset:16%; border-radius:50%; background:var(--surface-card); content:''; }
    .donut > div { position:absolute; inset:0; display:grid; align-content:center; justify-items:center; gap:.1rem; }
    .donut strong { color:var(--text-strong); font-family:var(--font-display); font-size:28px; letter-spacing:-.02em; }.donut small { color:var(--text-subtle); font-size:12px; }
    .legend { display:grid; gap:.6rem; }.legend-row { display:grid; grid-template-columns:.6rem minmax(0,1fr) auto; align-items:center; gap:.6rem; }
    .legend-row i { width:.6rem; height:.6rem; border-radius:50%; }.legend-row span { overflow:hidden; color:var(--text-muted); font-size:13px; text-overflow:ellipsis; white-space:nowrap; }.legend-row strong { color:var(--text-strong); font-size:14px; }
    .line-layout { display:grid; gap:.7rem; }.line-svg { display:block; width:100%; height:9.5rem; overflow:visible; }.line-area { fill:var(--color-primary); fill-opacity:.1; }.line-stroke { fill:none; stroke:var(--color-primary); stroke-width:3; stroke-linecap:round; stroke-linejoin:round; }.line-dot { fill:var(--surface-card); stroke:var(--color-primary); stroke-width:2.5; }
    .line-labels { display:grid; grid-auto-columns:minmax(0,1fr); grid-auto-flow:column; gap:.45rem; }.line-labels div { display:grid; gap:.18rem; min-width:0; }.line-labels small,.line-labels strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.line-labels small { color:var(--text-subtle); font-size:12px; }.line-labels strong { color:var(--text-strong); font-size:13px; }
    .bars-layout { display:grid; gap:.82rem; }.bar-row { display:grid; gap:.36rem; }.bar-row > div { display:flex; justify-content:space-between; gap:.65rem; }.bar-row > div span { color:var(--text-muted); font-size:13px; }.bar-row > div strong { color:var(--text-strong); font-size:14px; }.bar-track { overflow:hidden; height:.6rem; border-radius:2rem; background:var(--surface-muted); }.bar-track i { display:block; height:100%; min-width:.28rem; border-radius:inherit; background:var(--color-primary); transition:width .45s cubic-bezier(.2,.8,.2,1); }.bar-row > small { color:var(--text-subtle); font-size:12px; }
    @media(max-width:480px) { .donut-layout { grid-template-columns:1fr; }.donut { max-width:8rem; } }
  `,
})
export class InsightChartComponent {
  readonly points = input.required<readonly InsightChartPoint[]>();
  readonly variant = input<'bars' | 'donut' | 'line'>('bars');
  readonly format = input<'currency' | 'number'>('number');
  readonly totalLabel = input('registros');
  readonly emptyMessage = input('Los datos aparecerán cuando exista actividad registrada.');

  /** Categorías del donut, en los azules de la marca y con luminosidades distintas entre vecinas. */
  private readonly palette = ['#1f5c99', '#7cc4ea', '#0b2545', '#d9a441', '#4fa3a5', '#9aa8b6', '#b42318'];

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
