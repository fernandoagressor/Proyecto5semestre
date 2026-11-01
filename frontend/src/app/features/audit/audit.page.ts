import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { catchError, EMPTY, expand, map, of, reduce, switchMap } from 'rxjs';

import { AnomalyAlert, AuditEvent, AuditEventQuery } from '../../core/models/auditoria.models';
import { AuditoriaService } from '../../core/services/auditoria.service';
import { AuthService } from '../../core/services/auth.service';
import { TaskService, TeamTask } from '../../core/services/task.service';
import { TeamService } from '../../core/services/team.service';
import {
  apiErrorMessage,
  BadgeTone,
  ChipOption,
  formatDateTime,
  formatRelative,
  localDateTimeIso,
  NX_UI,
  statusLabel,
  ToastService,
} from '../../shared/ui';
import { buildAuditCsv, downloadText } from './audit-csv';

type Range = '24h' | '7d' | '30d' | 'todo';
type AlertFilter = 'abiertas' | 'cerradas' | 'todas';

const KNOWN_MODULES = ['SEGURIDAD', 'INVENTARIO', 'VENTAS', 'CARTERA', 'CRM', 'SERVICIO', 'PORTAL', 'GERENCIA', 'TAREAS', 'REPORTES', 'AUDITORIA'];
const MODULE_TONES: Record<string, BadgeTone> = { CARTERA: 'success', VENTAS: 'info', SEGURIDAD: 'danger', INVENTARIO: 'warning', AUDITORIA: 'danger' };
const ALERT_TYPES: Record<string, string> = {
  CUENTA_BLOQUEADA: 'Cuenta bloqueada por intentos fallidos',
  ACCESO_DESDE_IP_NUEVA: 'Acceso desde una IP nueva',
  EXPORTACIONES_REPETIDAS: 'Exportaciones repetidas de la bitácora',
  ACCION_ADMIN_FUERA_DE_HORARIO: 'Acción administrativa fuera de horario',
};
const MAX_EXPORT_ROWS = 2000;

/**
 * Auditoría y alertas (vista "Auditoria" del diseño): alertas de anomalías con asignación y
 * cierre, bitácora filtrable con el antes y el después de cada cambio, exportación a CSV con
 * motivo (queda registrada) y tareas vencidas del equipo.
 */
@Component({
  selector: 'app-audit-page',
  imports: [...NX_UI],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit.page.html',
  styleUrl: './audit.page.scss',
})
export class AuditPage {
  private readonly api = inject(AuditoriaService);
  private readonly tasks = inject(TaskService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  readonly team = inject(TeamService);

  readonly seesEvents = this.auth.hasAuthority('AUDITORIA_VER');
  readonly seesAlerts = this.auth.hasAuthority('ALERTAS_ANOMALIAS_VER');
  readonly manageAlerts = this.auth.hasAuthority('AUDITORIA_ALERTAS_GESTIONAR');
  readonly canExport = this.auth.hasAuthority('AUDITORIA_EXPORTAR');
  readonly seesTasks = this.auth.hasAuthority('TAREAS_VER');

  // Bitácora
  readonly module = signal('');
  readonly actor = signal('');
  readonly range = signal<Range>('7d');
  readonly action = signal('');
  readonly events = signal<AuditEvent[]>([]);
  readonly page = signal(0);
  readonly hasMore = signal(false);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly error = signal<string | null>(null);
  readonly open = signal<number | null>(null);
  readonly moduleOptions = computed(() => [...new Set([...KNOWN_MODULES, ...this.events().map((e) => e.modulo)])].sort());
  readonly rangeOptions: ChipOption[] = [
    { value: '24h', label: 'Últimas 24 h' },
    { value: '7d', label: '7 días' },
    { value: '30d', label: '30 días' },
    { value: 'todo', label: 'Todo' },
  ];

  // Alertas
  readonly alerts = signal<AnomalyAlert[]>([]);
  readonly alertsLoading = signal(true);
  readonly alertsError = signal<string | null>(null);
  readonly alertFilter = signal<AlertFilter>('abiertas');
  readonly alertOptions = computed<ChipOption[]>(() => {
    const all = this.alerts();
    const isOpen = (a: AnomalyAlert) => a.estado === 'ABIERTA' || a.estado === 'EN_INVESTIGACION';
    return [
      { value: 'abiertas', label: 'Abiertas', count: all.filter(isOpen).length },
      { value: 'cerradas', label: 'Cerradas', count: all.filter((a) => !isOpen(a)).length },
      { value: 'todas', label: 'Todas', count: all.length },
    ];
  });
  readonly visibleAlerts = computed(() => {
    const f = this.alertFilter();
    const isOpen = (a: AnomalyAlert) => a.estado === 'ABIERTA' || a.estado === 'EN_INVESTIGACION';
    return this.alerts().filter((a) => (f === 'todas' ? true : f === 'abiertas' ? isOpen(a) : !isOpen(a)));
  });
  readonly alertTarget = signal<AnomalyAlert | null>(null);
  readonly assignOpen = signal(false);
  readonly resolveOpen = signal(false);
  readonly assignTo = signal('');
  readonly resolution = signal('');
  readonly discard = signal(false);
  readonly alertBusy = signal(false);

  // Exportación
  readonly exportOpen = signal(false);
  readonly exportReason = signal('');
  readonly exporting = signal(false);

  // Tareas vencidas
  readonly overdue = signal<TeamTask[]>([]);

  constructor() {
    this.team.load();
    if (this.seesAlerts) this.loadAlerts();
    if (this.seesTasks) {
      this.tasks
        .list({ estado: 'VENCIDA', tamano: 8 })
        .pipe(catchError(() => of(null)))
        .subscribe((page) => this.overdue.set(page?.contenido ?? []));
    }
    effect(() => {
      const query = this.query();
      if (this.seesEvents) untracked(() => this.loadEvents(query));
    });
  }

  /** Filtros actuales de la bitácora, en el formato de la API. */
  readonly query = computed<AuditEventQuery>(() => {
    const hours: Record<Range, number | null> = { '24h': 24, '7d': 24 * 7, '30d': 24 * 30, todo: null };
    const h = hours[this.range()];
    return {
      modulo: this.module() || undefined,
      actorId: this.actor() ? Number(this.actor()) : null,
      accion: this.action().trim().toUpperCase() || undefined,
      desde: h ? localDateTimeIso(new Date(Date.now() - h * 3600_000)) : undefined,
    };
  });

  loadEvents(query = this.query()): void {
    this.loading.set(true);
    this.error.set(null);
    this.open.set(null);
    this.api.events({ ...query, pagina: 0, tamano: 50 }).subscribe({
      next: (page) => {
        this.events.set(page.contenido);
        this.page.set(0);
        this.total.set(page.totalElementos);
        this.hasMore.set(!page.ultima);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudo cargar la bitácora.'));
        this.loading.set(false);
      },
    });
  }

  loadMore(): void {
    const next = this.page() + 1;
    this.loadingMore.set(true);
    this.api.events({ ...this.query(), pagina: next, tamano: 50 }).subscribe({
      next: (page) => {
        this.events.update((list) => [...list, ...page.contenido]);
        this.page.set(next);
        this.hasMore.set(!page.ultima);
        this.loadingMore.set(false);
      },
      error: (e) => {
        this.loadingMore.set(false);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }

  applyAction(value: string): void {
    if (value.trim() !== this.action()) this.action.set(value.trim());
  }

  clearFilters(): void {
    this.module.set('');
    this.actor.set('');
    this.action.set('');
    this.range.set('7d');
  }

  toggle(id: number): void {
    this.open.set(this.open() === id ? null : id);
  }

  moduleTone(module: string): BadgeTone {
    return MODULE_TONES[module] ?? 'neutral';
  }

  actorName(id: number | null): string {
    return id === null ? 'Sistema' : this.team.name(id);
  }

  label(value: string | null): string {
    return statusLabel(value);
  }

  time(value: string): string {
    return new Date(value).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  }

  day(value: string): string {
    return formatDateTime(value);
  }

  relative(value: string | null): string {
    return formatRelative(value);
  }

  /** JSON de antes/después en líneas "campo: valor" legibles. */
  pretty(json: string | null): string {
    if (!json) return '—';
    try {
      const value = JSON.parse(json);
      if (value === null || typeof value !== 'object') return String(value);
      return Object.entries(value)
        .map(([k, v]) => `${k}: ${typeof v === 'object' && v !== null ? JSON.stringify(v) : v}`)
        .join('\n');
    } catch {
      return json;
    }
  }

  // ---------- Alertas ----------
  loadAlerts(): void {
    this.alertsLoading.set(true);
    this.alertsError.set(null);
    this.api.alerts().subscribe({
      next: (page) => {
        this.alerts.set(page.contenido);
        this.alertsLoading.set(false);
      },
      error: (e) => {
        this.alertsError.set(apiErrorMessage(e, 'No se pudieron cargar las alertas.'));
        this.alertsLoading.set(false);
      },
    });
  }

  alertTitle(a: AnomalyAlert): string {
    return ALERT_TYPES[a.tipo] ?? statusLabel(a.tipo);
  }

  setAlertFilter(value: string): void {
    this.alertFilter.set(value as AlertFilter);
  }

  askAssign(a: AnomalyAlert): void {
    this.alertTarget.set(a);
    this.assignTo.set(a.asignadoA ? String(a.asignadoA) : String(this.auth.user()?.id ?? ''));
    this.assignOpen.set(true);
  }

  askResolve(a: AnomalyAlert, discard: boolean): void {
    this.alertTarget.set(a);
    this.resolution.set('');
    this.discard.set(discard);
    this.resolveOpen.set(true);
  }

  confirmAssign(): void {
    const a = this.alertTarget();
    if (!a || !this.assignTo()) return;
    this.alertBusy.set(true);
    this.api.assignAlert(a.id, Number(this.assignTo())).subscribe({
      next: () => this.alertDone('Alerta asignada. Queda en investigación.', () => this.assignOpen.set(false)),
      error: (e) => this.alertFailed(e),
    });
  }

  confirmResolve(): void {
    const a = this.alertTarget();
    const text = this.resolution().trim();
    if (!a || !text) return;
    this.alertBusy.set(true);
    this.api.resolveAlert(a.id, text, this.discard()).subscribe({
      next: () => this.alertDone(this.discard() ? 'Alerta descartada.' : 'Alerta resuelta.', () => this.resolveOpen.set(false)),
      error: (e) => this.alertFailed(e),
    });
  }

  private alertDone(message: string, close: () => void): void {
    this.alertBusy.set(false);
    close();
    this.toast.success(message);
    this.loadAlerts();
  }

  private alertFailed(e: unknown): void {
    this.alertBusy.set(false);
    this.toast.error(apiErrorMessage(e));
  }

  // ---------- Exportación ----------
  confirmExport(): void {
    const reason = this.exportReason().trim();
    if (!reason) return;
    const query = this.query();
    this.exporting.set(true);
    this.api
      .registerExport(reason, 'CSV', { ...query })
      .pipe(
        switchMap(() =>
          this.api.events({ ...query, pagina: 0, tamano: 100 }).pipe(
            expand((page) => (page.ultima || (page.pagina + 1) * 100 >= MAX_EXPORT_ROWS ? EMPTY : this.api.events({ ...query, pagina: page.pagina + 1, tamano: 100 }))),
            map((page) => page.contenido),
            reduce((all, rows) => [...all, ...rows], [] as AuditEvent[]),
          ),
        ),
      )
      .subscribe({
        next: (rows) => {
          const csv = buildAuditCsv(rows, (id) => this.actorName(id));
          const stamp = localDateTimeIso(new Date()).replace(/[-:]/g, '').replace('T', '-').slice(0, 13);
          downloadText(`bitacora-auditoria-${stamp}.csv`, csv, 'text/csv;charset=utf-8');
          this.exporting.set(false);
          this.exportOpen.set(false);
          this.exportReason.set('');
          this.toast.success(`Bitácora exportada: ${rows.length} eventos${rows.length >= MAX_EXPORT_ROWS ? ' (máximo por archivo)' : ''}.`);
        },
        error: (e) => {
          this.exporting.set(false);
          this.toast.error(apiErrorMessage(e, 'No se pudo exportar la bitácora.'));
        },
      });
  }
}
