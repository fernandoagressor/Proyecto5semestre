import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, Observable, of } from 'rxjs';

import {
  ClientDashboard,
  CommercialDashboard,
  DashboardDateRange,
  DashboardData,
  DashboardKind,
  DashboardPeriod,
  DashboardSeries,
  ManagementDashboard,
  PortfolioDashboard,
  ServiceDashboard,
} from '../../core/models/dashboard.models';
import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { ProjectService } from '../../core/services/project.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';
import { InsightChartComponent, InsightChartPoint } from '../../shared/components/insight-chart.component';

interface MetricCard {
  label: string;
  value: string;
  detail: string;
  tone: 'blue' | 'green' | 'orange' | 'violet';
  icon: string;
}

interface QuickAction {
  label: string;
  caption: string;
  route: string;
  icon: string;
  permissions: string[];
  permissionMode?: 'any' | 'all';
}

interface DashboardViewOption {
  kind: DashboardKind;
  label: string;
  icon: string;
  permission: string;
}

interface DashboardPeriodOption {
  value: DashboardPeriod;
  label: string;
}

interface PerformanceIndicator {
  label: string;
  value: number;
  display: string;
  tone: 'blue' | 'green' | 'orange' | 'violet';
  detail: string;
}

interface DashboardAlert {
  title: string;
  description: string;
  tone: 'green' | 'orange' | 'violet';
  icon: string;
}

@Component({
  selector: 'app-dashboard-page',
  imports: [RouterLink, AppIconComponent, InsightChartComponent],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly dashboardService = inject(DashboardService);
  private readonly projectService = inject(ProjectService);
  readonly auth = inject(AuthService);

  readonly kind = signal<DashboardKind>(this.dashboardService.availableKind());
  readonly data = signal<DashboardData | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly selectedProjectId = signal<number | null>(null);
  readonly period = signal<DashboardPeriod>('history');
  readonly projects = signal<Project[]>([]);
  readonly relatedData = signal<Partial<Record<DashboardKind, DashboardData>>>({});
  readonly relatedLoading = signal(false);

  readonly dashboardViews: readonly DashboardViewOption[] = [
    { kind: 'gerencial', label: 'Gerencia', icon: 'management', permission: 'DASHBOARD_GERENCIAL_VER' },
    { kind: 'comercial', label: 'Comercial', icon: 'sales', permission: 'DASHBOARD_COMERCIAL_VER' },
    { kind: 'cartera', label: 'Cartera', icon: 'wallet', permission: 'DASHBOARD_CARTERA_VER' },
    { kind: 'servicio', label: 'Servicio', icon: 'service', permission: 'DASHBOARD_SERVICIO_VER' },
  ];
  readonly periodOptions: readonly DashboardPeriodOption[] = [
    { value: 'history', label: 'Todo el historial' },
    { value: 'month', label: 'Mes actual' },
    { value: 'quarter', label: 'Últimos 3 meses' },
    { value: 'year', label: 'Año actual' },
  ];
  readonly availableViews = computed(() => this.dashboardViews
    .filter((view) => this.auth.hasAuthority(view.permission)));
  readonly supportsPeriodFilter = computed(() =>
    this.kind() === 'gerencial' || this.kind() === 'comercial' || this.kind() === 'servicio');

  readonly greeting = computed(() => {
    const hour = new Date().getHours();
    return hour < 12 ? 'Buenos días' : hour < 18 ? 'Buenas tardes' : 'Buenas noches';
  });
  readonly today = new Intl.DateTimeFormat('es-CO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());

  readonly title = computed(() => {
    const labels: Record<DashboardKind, string> = {
      gerencial: 'Visión ejecutiva del negocio',
      comercial: 'Rendimiento del equipo comercial',
      cartera: 'Salud financiera de la cartera',
      servicio: 'Experiencia y atención al cliente',
      cliente: 'Resumen de tu inversión',
    };
    return labels[this.kind()];
  });

  readonly periodDescription = computed(() => {
    const data = this.data();
    if (this.kind() === 'cliente') return 'Información vigente del cliente';
    if (this.kind() === 'cartera') {
      const corte = (data as PortfolioDashboard | null)?.corte;
      return corte ? `Corte de cartera: ${this.formatPeriodDate(corte)}` : 'Corte de cartera a hoy';
    }

    const selected = this.periodOptions.find((option) => option.value === this.period());
    const range = data as ManagementDashboard | CommercialDashboard | ServiceDashboard | null;
    if (this.period() === 'history') {
      return range?.hasta
        ? `Todo el historial · corte ${this.formatPeriodDate(range.hasta)}`
        : 'Todo el historial';
    }
    if (!range?.desde || !range.hasta) return selected?.label ?? 'Periodo seleccionado';
    return `${selected?.label} · ${this.formatPeriodDate(range.desde)} – ${this.formatPeriodDate(range.hasta)}`;
  });

  readonly metrics = computed<MetricCard[]>(() => {
    const data = this.data();
    if (!data) return [];

    switch (this.kind()) {
      case 'gerencial': {
        const item = data as ManagementDashboard;
        return [
          { label: 'Ventas del periodo', value: this.money(item.ventas), detail: `${item.contratos} contratos`, tone: 'blue', icon: 'sales' },
          { label: 'Recaudo', value: this.money(item.recaudo), detail: 'Ingresos confirmados', tone: 'green', icon: 'wallet' },
          { label: 'Cartera vencida', value: this.money(item.carteraVencida), detail: `${this.percent(item.indiceMora)} de mora`, tone: 'orange', icon: 'warning' },
          { label: 'Utilidad', value: this.money(item.utilidad), detail: `${this.percent(item.margen)} de margen`, tone: 'violet', icon: 'management' },
        ];
      }
      case 'comercial': {
        const item = data as CommercialDashboard;
        return [
          { label: 'Valor del embudo', value: this.money(item.valorEmbudo), detail: `${item.oportunidadesNuevas} oportunidades nuevas`, tone: 'blue', icon: 'crm' },
          { label: 'Oportunidades ganadas', value: this.number(item.oportunidadesGanadas), detail: `${this.percent(item.tasaConversion)} de conversión`, tone: 'green', icon: 'sales' },
          { label: 'Reservas activas', value: this.number(item.reservasActivas), detail: 'En proceso comercial', tone: 'violet', icon: 'inventory' },
          { label: 'Oportunidades perdidas', value: this.number(item.oportunidadesPerdidas), detail: 'En el periodo seleccionado', tone: 'orange', icon: 'warning' },
        ];
      }
      case 'cartera': {
        const item = data as PortfolioDashboard;
        return [
          { label: 'Saldo total', value: this.money(item.saldoTotal), detail: 'Cartera por recaudar', tone: 'blue', icon: 'wallet' },
          { label: 'Recaudo del mes', value: this.money(item.recaudoMes), detail: 'Pagos confirmados', tone: 'green', icon: 'sales' },
          { label: 'Saldo vencido', value: this.money(item.saldoVencido), detail: `${item.cuotasVencidas} cuotas vencidas`, tone: 'orange', icon: 'warning' },
          { label: 'Índice de mora', value: this.percent(item.indiceMora), detail: `${item.promesasPendientes} promesas pendientes`, tone: 'violet', icon: 'management' },
        ];
      }
      case 'servicio': {
        const item = data as ServiceDashboard;
        return [
          { label: 'Solicitudes abiertas', value: this.number(item.solicitudesAbiertas), detail: `${item.solicitudesVencidas} fuera de SLA`, tone: 'blue', icon: 'service' },
          { label: 'Satisfacción', value: `${this.number(item.satisfaccionPromedio, 1)} / 5`, detail: 'Promedio del periodo', tone: 'green', icon: 'management' },
          { label: 'Primera respuesta', value: `${this.number(item.horasPrimeraRespuesta, 1)} h`, detail: 'Tiempo promedio', tone: 'violet', icon: 'refresh' },
          { label: 'Pendientes', value: this.number(item.citasPendientes + item.ticketsAbiertos), detail: `${item.citasPendientes} citas · ${item.ticketsAbiertos} tickets`, tone: 'orange', icon: 'calendar' },
        ];
      }
      default: {
        const item = data as ClientDashboard;
        return [
          { label: 'Saldo pendiente', value: this.money(item.saldoPendiente), detail: `${item.cuotasPendientes} cuotas pendientes`, tone: 'blue', icon: 'wallet' },
          { label: 'Contratos vigentes', value: this.number(item.contratosVigentes), detail: `Cliente ${item.codigoCliente}`, tone: 'green', icon: 'sales' },
          { label: 'Documentos', value: this.number(item.documentosDisponibles), detail: 'Disponibles en el portal', tone: 'violet', icon: 'projects' },
          { label: 'Solicitudes abiertas', value: this.number(item.solicitudesAbiertas), detail: item.proximaCita ? 'Próxima cita programada' : 'Sin citas próximas', tone: 'orange', icon: 'service' },
        ];
      }
    }
  });

  readonly chartSeries = computed<DashboardSeries[]>(() => {
    const data = this.data();
    if (!data) return [];
    switch (this.kind()) {
      case 'gerencial': {
        const item = data as ManagementDashboard;
        return [
          { categoria: 'Ventas', valor: item.ventas, cantidad: item.contratos },
          { categoria: 'Recaudo', valor: item.recaudo, cantidad: 0 },
          { categoria: 'Cartera', valor: item.carteraTotal, cantidad: 0 },
          { categoria: 'Inventario', valor: item.inventarioDisponible, cantidad: item.lotesDisponibles },
        ];
      }
      case 'comercial': return (data as CommercialDashboard).embudo;
      case 'cartera': return (data as PortfolioDashboard).edadesMora;
      case 'servicio': return (data as ServiceDashboard).solicitudesPorTipo;
      default: return [];
    }
  });

  readonly chartTitle = computed(() => {
    const labels: Record<DashboardKind, string> = {
      gerencial: 'Panorama financiero',
      comercial: 'Embudo por etapa',
      cartera: 'Edades de mora',
      servicio: 'Solicitudes por tipo',
      cliente: 'Estado general',
    };
    return labels[this.kind()];
  });

  readonly financeSeries = computed<InsightChartPoint[]>(() => {
    const management = this.dashboardOf('gerencial') as ManagementDashboard | null;
    if (management) {
      return [
        { categoria: 'Ventas', valor: management.ventas, cantidad: management.contratos },
        { categoria: 'Recaudo', valor: management.recaudo, cantidad: 0 },
        { categoria: 'Cartera', valor: management.carteraTotal, cantidad: 0 },
        { categoria: 'Inventario', valor: management.inventarioDisponible, cantidad: management.lotesDisponibles },
      ];
    }
    const portfolio = this.dashboardOf('cartera') as PortfolioDashboard | null;
    if (portfolio) {
      return [
        { categoria: 'Saldo total', valor: portfolio.saldoTotal, cantidad: 0 },
        { categoria: 'Recaudo', valor: portfolio.recaudoMes, cantidad: 0 },
        { categoria: 'Saldo vencido', valor: portfolio.saldoVencido, cantidad: portfolio.cuotasVencidas },
      ];
    }
    const commercial = this.dashboardOf('comercial') as CommercialDashboard | null;
    return commercial?.ventasPorAsesor ?? [];
  });

  readonly distributionSeries = computed<InsightChartPoint[]>(() => {
    const data = this.data();
    if (!data) return [];
    switch (this.kind()) {
      case 'gerencial': {
        const item = data as ManagementDashboard;
        return this.countSeries([
          ['Lotes disponibles', item.lotesDisponibles],
          ['Oportunidades', item.oportunidadesAbiertas],
          ['Contratos', item.contratos],
          ['Tareas vencidas', item.tareasVencidas],
          ['PQRS abiertas', item.pqrsAbiertas],
        ]);
      }
      case 'comercial': {
        const item = data as CommercialDashboard;
        return this.countSeries([
          ['Ganadas', item.oportunidadesGanadas],
          ['Perdidas', item.oportunidadesPerdidas],
          ['Reservas activas', item.reservasActivas],
          ['Nuevas', item.oportunidadesNuevas],
        ]);
      }
      case 'cartera': {
        const item = data as PortfolioDashboard;
        return item.edadesMora.length > 0 ? item.edadesMora : this.countSeries([
          ['Cuotas vencidas', item.cuotasVencidas],
          ['Promesas de pago', item.promesasPendientes],
        ]);
      }
      case 'servicio': {
        const item = data as ServiceDashboard;
        return this.countSeries([
          ['Solicitudes', item.solicitudesAbiertas],
          ['Fuera de SLA', item.solicitudesVencidas],
          ['Citas pendientes', item.citasPendientes],
          ['Tickets abiertos', item.ticketsAbiertos],
        ]);
      }
      default: {
        const item = data as ClientDashboard;
        return this.countSeries([
          ['Contratos', item.contratosVigentes],
          ['Cuotas pendientes', item.cuotasPendientes],
          ['Documentos', item.documentosDisponibles],
          ['Solicitudes', item.solicitudesAbiertas],
        ]);
      }
    }
  });

  readonly operationsSeries = computed<InsightChartPoint[]>(() => {
    const management = this.dashboardOf('gerencial') as ManagementDashboard | null;
    const commercial = this.dashboardOf('comercial') as CommercialDashboard | null;
    const portfolio = this.dashboardOf('cartera') as PortfolioDashboard | null;
    const service = this.dashboardOf('servicio') as ServiceDashboard | null;
    return this.countSeries([
      ['Contratos', management?.contratos ?? 0],
      ['Reservas activas', commercial?.reservasActivas ?? 0],
      ['Lotes disponibles', management?.lotesDisponibles ?? 0],
      ['Oportunidades', management?.oportunidadesAbiertas ?? commercial?.oportunidadesNuevas ?? 0],
      ['Cuotas vencidas', portfolio?.cuotasVencidas ?? 0],
      ['Solicitudes', service?.solicitudesAbiertas ?? management?.pqrsAbiertas ?? 0],
    ]);
  });

  readonly projectSeries = computed<InsightChartPoint[]>(() => this.projects()
    .filter((project) => !this.selectedProjectId() || project.id === this.selectedProjectId())
    .slice(0, 6)
    .map((project) => ({
      categoria: project.nombre,
      valor: Number(project.porcentajeAvance ?? 0),
      cantidad: Number(project.porcentajeAvance ?? 0),
    })));

  readonly advisorSeries = computed<InsightChartPoint[]>(() => {
    const commercial = this.dashboardOf('comercial') as CommercialDashboard | null;
    return commercial?.ventasPorAsesor.slice(0, 6) ?? [];
  });

  readonly performance = computed<PerformanceIndicator[]>(() => {
    const management = this.dashboardOf('gerencial') as ManagementDashboard | null;
    const commercial = this.dashboardOf('comercial') as CommercialDashboard | null;
    const portfolio = this.dashboardOf('cartera') as PortfolioDashboard | null;
    const service = this.dashboardOf('servicio') as ServiceDashboard | null;
    const indicators: PerformanceIndicator[] = [];
    if (management) {
      const collection = management.ventas > 0 ? management.recaudo / management.ventas * 100 : 0;
      indicators.push({ label: 'Recaudo sobre ventas', value: this.clamp(collection), display: this.percent(collection), tone: 'blue', detail: 'Ingresos confirmados frente al cierre comercial' });
      indicators.push({ label: 'Margen operativo', value: this.clamp(Number(management.margen)), display: this.percent(management.margen), tone: 'green', detail: 'Utilidad calculada desde ventas y costos reales' });
    }
    if (commercial) indicators.push({ label: 'Conversión comercial', value: this.clamp(Number(commercial.tasaConversion)), display: this.percent(commercial.tasaConversion), tone: 'violet', detail: 'Oportunidades ganadas durante el periodo' });
    if (portfolio) {
      const health = this.clamp(100 - Number(portfolio.indiceMora));
      indicators.push({ label: 'Cartera al día', value: health, display: this.percent(health), tone: 'green', detail: `${this.number(portfolio.cuotasVencidas)} cuotas presentan vencimiento` });
    }
    if (service) {
      const satisfaction = this.clamp(Number(service.satisfaccionPromedio) / 5 * 100);
      indicators.push({ label: 'Satisfacción', value: satisfaction, display: `${this.number(service.satisfaccionPromedio, 1)} / 5`, tone: 'orange', detail: 'Evaluaciones reales de clientes' });
    }
    return indicators.slice(0, 5);
  });

  readonly alerts = computed<DashboardAlert[]>(() => {
    const management = this.dashboardOf('gerencial') as ManagementDashboard | null;
    const portfolio = this.dashboardOf('cartera') as PortfolioDashboard | null;
    const service = this.dashboardOf('servicio') as ServiceDashboard | null;
    const notices: DashboardAlert[] = [];
    if (portfolio?.cuotasVencidas) notices.push({ title: `${this.number(portfolio.cuotasVencidas)} cuotas vencidas`, description: `Saldo por regularizar: ${this.money(portfolio.saldoVencido)}.`, tone: 'orange', icon: 'wallet' });
    if (service?.solicitudesVencidas) notices.push({ title: `${this.number(service.solicitudesVencidas)} solicitudes fuera de SLA`, description: 'Revisa los casos que requieren atención inmediata.', tone: 'orange', icon: 'service' });
    if (management?.tareasVencidas) notices.push({ title: `${this.number(management.tareasVencidas)} tareas vencidas`, description: 'Actualiza los compromisos operativos pendientes.', tone: 'violet', icon: 'calendar' });
    if (notices.length === 0) notices.push({ title: 'Operación bajo control', description: 'No se detectan alertas prioritarias con tu nivel de acceso.', tone: 'green', icon: 'audit' });
    return notices;
  });

  readonly quickActions: QuickAction[] = [
    { label: 'Ver proyectos', caption: 'Consulta el portafolio activo', route: '/proyectos', icon: 'projects', permissions: ['PROYECTOS_VER'] },
    { label: 'Gestionar inventario', caption: 'Revisa lotes y disponibilidad', route: '/inventario', icon: 'inventory', permissions: ['LOTES_VER'] },
    { label: 'Crear cliente', caption: 'Registra y vincula un lote', route: '/clientes/nuevo', icon: 'crm', permissions: ['CLIENTES_CREAR', 'PROSPECTOS_CREAR', 'PROSPECTOS_CALIFICAR', 'OPORTUNIDADES_CREAR', 'OPORTUNIDADES_EDITAR', 'RESERVAS_CREAR', 'PROYECTOS_VER', 'LOTES_VER'], permissionMode: 'all' },
    { label: 'Revisar cartera', caption: 'Pagos, cuotas y mora', route: '/cartera', icon: 'wallet', permissions: ['PAGOS_VER', 'CUOTAS_VER'] },
  ];

  constructor() {
    if (this.auth.hasAuthority('PROYECTOS_VER')) {
      this.projectService
        .list({ size: 100 })
        .pipe(catchError(() => of(null)))
        .subscribe((page) => this.projects.set(page?.contenido ?? []));
    }
    this.load();
  }

  selectProject(value: string): void {
    this.selectedProjectId.set(value ? Number(value) : null);
    this.load();
  }

  selectPeriod(value: string): void {
    const selected = this.periodOptions.find((option) => option.value === value)?.value;
    if (!selected || selected === this.period()) return;
    this.period.set(selected);
    this.load();
  }

  selectKind(kind: DashboardKind): void {
    if (kind === this.kind() || !this.availableViews().some((view) => view.kind === kind)) return;
    this.kind.set(kind);
    const existing = this.relatedData()[kind];
    if (existing) {
      this.data.set(existing);
      return;
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.relatedData.set({});
    const currentKind = this.kind();
    const currentProject = this.selectedProjectId();
    const range = dashboardDateRange(this.period());
    this.loadDashboard(currentKind, currentProject, range)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => {
          this.data.set(data);
          this.relatedData.set({ [currentKind]: data });
          this.loadRelatedDashboards(currentKind, currentProject, range);
        },
        error: () => {
          this.data.set(null);
          this.error.set('No pudimos cargar los indicadores en este momento.');
        },
      });
  }

  isQuickActionVisible(action: QuickAction): boolean {
    return action.permissionMode === 'all'
      ? this.auth.hasAll(action.permissions)
      : this.auth.hasAny(action.permissions);
  }

  barWidth(item: DashboardSeries): number {
    const values = this.chartSeries().map((entry) => Math.max(Number(entry.valor || 0), entry.cantidad || 0));
    const max = Math.max(...values, 1);
    return Math.max(5, (Math.max(Number(item.valor || 0), item.cantidad || 0) / max) * 100);
  }

  seriesValue(item: DashboardSeries): string {
    return Number(item.valor) > 0 ? this.money(item.valor) : this.number(item.cantidad);
  }

  money(value: number | string | null | undefined): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
      notation: Number(value ?? 0) >= 1_000_000_000 ? 'compact' : 'standard',
    }).format(Number(value ?? 0));
  }

  number(value: number | string | null | undefined, decimals = 0): string {
    return new Intl.NumberFormat('es-CO', {
      maximumFractionDigits: decimals,
      minimumFractionDigits: decimals,
    }).format(Number(value ?? 0));
  }

  percent(value: number | string | null | undefined): string {
    return `${this.number(value, 1)} %`;
  }

  private loadRelatedDashboards(
    current: DashboardKind,
    projectId: number | null,
    range: DashboardDateRange | undefined,
  ): void {
    const views = this.availableViews().filter((view) => view.kind !== current);
    if (views.length === 0) return;
    this.relatedLoading.set(true);
    const requests = Object.fromEntries(views.map((view) => [
      view.kind,
      this.loadDashboard(view.kind, projectId, range).pipe(catchError(() => of(null))),
    ]));
    forkJoin(requests)
      .pipe(finalize(() => this.relatedLoading.set(false)))
      .subscribe((results) => {
        if (projectId !== this.selectedProjectId()) return;
        const loaded = Object.fromEntries(Object.entries(results).filter(([, result]) => result !== null));
        this.relatedData.update((existing) => ({ ...existing, ...loaded }));
      });
  }

  private dashboardOf(kind: DashboardKind): DashboardData | null {
    return (this.kind() === kind ? this.data() : this.relatedData()[kind]) ?? null;
  }

  private loadDashboard(
    kind: DashboardKind,
    projectId: number | null,
    range: DashboardDateRange | undefined,
  ): Observable<DashboardData> {
    return range
      ? this.dashboardService.load(kind, projectId, range)
      : this.dashboardService.load(kind, projectId);
  }

  private formatPeriodDate(value: string): string {
    const [year, month, day] = value.split('-').map(Number);
    if (!year || !month || !day) return value;
    return new Intl.DateTimeFormat('es-CO', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    }).format(new Date(year, month - 1, day));
  }

  private countSeries(entries: Array<[string, number]>): InsightChartPoint[] {
    return entries.filter(([, count]) => Number(count) > 0)
      .map(([categoria, count]) => ({ categoria, valor: Number(count), cantidad: Number(count) }));
  }

  private clamp(value: number): number {
    return Math.min(100, Math.max(0, Number.isFinite(value) ? value : 0));
  }
}

export function dashboardDateRange(
  period: DashboardPeriod,
  today = new Date(),
): DashboardDateRange | undefined {
  if (period === 'history') return undefined;

  const hasta = localDateIso(today);
  if (period === 'month') {
    return { desde: localDateIso(new Date(today.getFullYear(), today.getMonth(), 1)), hasta };
  }
  if (period === 'quarter') {
    return { desde: localDateIso(new Date(today.getFullYear(), today.getMonth() - 2, 1)), hasta };
  }
  return { desde: localDateIso(new Date(today.getFullYear(), 0, 1)), hasta };
}

function localDateIso(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
