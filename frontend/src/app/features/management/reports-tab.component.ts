import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { Project } from '../../core/models/empresa.models';
import { DownloadService, ExportFormat, ReportType } from '../../core/services/download.service';
import { apiErrorMessage, NX_UI, todayIso, ToastService } from '../../shared/ui';

interface ReportCard {
  type: ReportType;
  title: string;
  text: string;
  icon: string;
  period: boolean;
}

const REPORTS: ReportCard[] = [
  { type: 'VENTAS', title: 'Ventas', text: 'Contratos firmados en el periodo con cliente, asesor, valor y cuota inicial.', icon: 'sales', period: true },
  { type: 'RECAUDO', title: 'Recaudo', text: 'Pagos confirmados con fecha, método, referencia y contrato.', icon: 'money', period: true },
  { type: 'CARTERA', title: 'Cartera vencida', text: 'Cuotas pendientes hasta la fecha final, ordenadas por días de mora.', icon: 'wallet', period: false },
  { type: 'INVENTARIO', title: 'Inventario', text: 'Todos los lotes con etapa, manzana, área, precio de lista y estado.', icon: 'inventory', period: false },
  { type: 'COMISIONES', title: 'Comisiones', text: 'Comisiones por asesor con base, porcentaje, estado y fechas.', icon: 'percent', period: true },
];

/** Reportes descargables en PDF, Excel o CSV (vista "Reportes" de Gerencia). */
@Component({
  selector: 'app-reports-tab',
  imports: [...NX_UI, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="nx-card nx-card--pad nx-card--stack">
      <div class="nx-toolbar">
        <label class="nx-field"><span>Desde</span><input class="nx-input" type="date" [(ngModel)]="desde" /></label>
        <label class="nx-field"><span>Hasta</span><input class="nx-input" type="date" [(ngModel)]="hasta" /></label>
        <span class="nx-muted small hint">{{ projectId() ? 'Solo el proyecto elegido arriba.' : 'Todos los proyectos a su alcance.' }}</span>
      </div>
    </section>
    <section class="reports">
      @for (r of reports; track r.type) {
        <article class="nx-card report">
          <span class="report__icon"><app-icon [name]="r.icon" /></span>
          <div class="report__body">
            <b>{{ r.title }}</b>
            <p>{{ r.text }}</p>
            <small class="nx-muted">{{ r.period ? 'Usa el periodo elegido.' : 'Corte a la fecha final.' }}</small>
          </div>
          <div class="report__actions">
            @for (f of formats; track f.value) {
              <button type="button" class="nx-btn nx-btn--sm" [class.nx-btn--primary]="f.value === 'PDF'" [disabled]="busy() === r.type + f.value" (click)="download(r.type, f.value)">
                <app-icon name="download" /> {{ busy() === r.type + f.value ? 'Generando…' : f.label }}
              </button>
            }
          </div>
        </article>
      }
    </section>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 14px; }
    .hint { align-self: center; }
    .small { font-size: 13px; }
    .reports { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(340px, 100%), 1fr)); gap: 14px; }
    .report { display: flex; flex-direction: column; gap: 12px; padding: 18px; }
    .report__icon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 12px; color: var(--color-primary); background: var(--color-primary-soft); }
    .report__icon app-icon { width: 20px; height: 20px; }
    .report__body b { font-family: var(--font-display); font-size: 17px; }
    .report__body p { margin: 4px 0; color: var(--text-muted); font-size: 14px; line-height: 1.45; }
    .report__body small { font-size: 12px; }
    .report__actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: auto; }
  `,
})
export class ReportsTabComponent {
  private readonly downloads = inject(DownloadService);
  private readonly toast = inject(ToastService);

  readonly projectId = input<number | null>(null);
  readonly projects = input<Project[]>([]);

  readonly reports = REPORTS;
  readonly formats: { value: ExportFormat; label: string }[] = [
    { value: 'PDF', label: 'PDF' },
    { value: 'XLSX', label: 'Excel' },
    { value: 'CSV', label: 'CSV' },
  ];
  readonly busy = signal<string>('');
  desde = `${todayIso().slice(0, 8)}01`;
  hasta = todayIso();

  download(type: ReportType, format: ExportFormat): void {
    this.busy.set(type + format);
    this.downloads
      .report(type, format, { desde: this.desde, hasta: this.hasta, proyectoId: this.projectId() })
      .pipe(finalize(() => this.busy.set('')))
      .subscribe({ error: (e) => this.toast.error(blobError(e, 'No se pudo generar el reporte.')) });
  }
}

/** Los errores de una descarga llegan como Blob: se muestra el mensaje genérico o el de la API si se puede. */
export function blobError(e: unknown, fallback: string): string {
  return apiErrorMessage(e, fallback);
}
