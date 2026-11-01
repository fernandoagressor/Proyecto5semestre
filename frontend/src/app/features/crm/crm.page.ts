import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, map, of } from 'rxjs';

import { PageResponse } from '../../core/models/api.models';
import { Opportunity, OpportunityStage } from '../../core/models/crm.models';
import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { ProjectService } from '../../core/services/project.service';
import { TeamService } from '../../core/services/team.service';
import { apiErrorMessage, ChipOption, currentPeriod, formatCopCompact, formatPercent, NX_UI, TabItem } from '../../shared/ui';
import { AgendaTabComponent } from './agenda-tab.component';
import { CrmNames } from './crm-names.service';
import { STAGES, stageOf, StageDefinition } from './crm-labels';
import { OpportunityDrawerComponent } from './opportunity-drawer.component';
import { ProspectFormComponent } from './prospect-form.component';
import { ProspectsTabComponent } from './prospects-tab.component';

interface Column {
  stage: StageDefinition;
  items: Opportunity[];
  total: number;
}

const PER_COLUMN = 12;

/**
 * Comercial (vista "Comercial-CRM" del diseño): embudo en tablero o tabla, detalle de cada
 * oportunidad en un panel lateral, prospectos y agenda.
 */
@Component({
  selector: 'app-crm-page',
  imports: [...NX_UI, ReactiveFormsModule, OpportunityDrawerComponent, ProspectFormComponent, ProspectsTabComponent, AgendaTabComponent],
  providers: [CrmNames],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './crm.page.html',
  styleUrl: './crm.page.scss',
})
export class CrmPage {
  private readonly crm = inject(CrmService);
  private readonly projectService = inject(ProjectService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  readonly names = inject(CrmNames);
  readonly team = inject(TeamService);

  readonly can = {
    opportunities: this.auth.hasAuthority('OPORTUNIDADES_VER'),
    prospects: this.auth.hasAuthority('PROSPECTOS_VER'),
    agenda: this.auth.hasAuthority('AGENDA_VER'),
    createProspect: this.auth.hasAuthority('PROSPECTOS_CREAR'),
  };

  readonly projects = signal<Project[]>([]);
  readonly projectId = signal<number | null>(null);
  readonly search = new FormControl('', { nonNullable: true });
  readonly text = toSignal(this.search.valueChanges.pipe(debounceTime(350), map((v) => v.trim()), distinctUntilChanged()), { initialValue: '' });

  readonly tabs = computed<TabItem[]>(() => [
    ...(this.can.opportunities ? [{ id: 'embudo', label: 'Embudo' }] : []),
    ...(this.can.prospects ? [{ id: 'prospectos', label: 'Prospectos' }] : []),
    ...(this.can.agenda ? [{ id: 'agenda', label: 'Agenda' }] : []),
  ]);
  readonly tab = signal(this.tabs()[0]?.id ?? 'embudo');
  readonly view = signal<'tablero' | 'tabla'>('tablero');
  readonly viewOptions: ChipOption[] = [
    { value: 'tablero', label: 'Tablero' },
    { value: 'tabla', label: 'Tabla' },
  ];

  // Tablero
  readonly columns = signal<Column[]>([]);
  readonly boardLoading = signal(true);
  readonly boardError = signal<string | null>(null);
  // Tabla
  readonly tableStatus = signal('ABIERTA');
  readonly tableStage = signal('');
  readonly tablePage = signal(0);
  readonly table = signal<PageResponse<Opportunity> | null>(null);
  readonly tableLoading = signal(false);
  readonly statusOptions: ChipOption[] = [
    { value: 'ABIERTA', label: 'Abiertas' },
    { value: 'GANADA', label: 'Ganadas' },
    { value: 'PERDIDA', label: 'Perdidas' },
    { value: '', label: 'Todas' },
  ];
  readonly stages = STAGES;
  // Indicadores del mes
  readonly closed = signal<{ won: Opportunity[]; lost: Opportunity[] }>({ won: [], lost: [] });

  readonly drawerOpen = signal(false);
  readonly selectedId = signal<number | null>(null);
  readonly prospectFormOpen = signal(false);
  readonly reloadKey = signal(0);

  readonly kpis = computed(() => {
    const cols = this.columns();
    const open = cols.reduce((s, c) => s + c.total, 0);
    const shown = cols.flatMap((c) => c.items);
    const value = shown.reduce((s, o) => s + Number(o.valorEstimado ?? 0), 0);
    const partial = shown.length < open;
    const { won, lost } = this.closed();
    const rate = won.length + lost.length > 0 ? (won.length / (won.length + lost.length)) * 100 : null;
    const days = won.length
      ? Math.round(won.reduce((s, o) => s + (new Date(o.fechaCierre ?? o.actualizadoEn).getTime() - new Date(o.creadoEn).getTime()) / 86_400_000, 0) / won.length)
      : null;
    return {
      value: formatCopCompact(value),
      valueHint: partial ? `De las ${shown.length} más recientes` : 'Valor estimado de las abiertas',
      open: open.toLocaleString('es-CO'),
      won: String(won.length),
      wonValue: formatCopCompact(won.reduce((s, o) => s + Number(o.valorEstimado ?? 0), 0)),
      rate: rate === null ? '—' : formatPercent(rate),
      rateHint: `${won.length} ganadas · ${lost.length} perdidas`,
      days: days === null ? '—' : String(days),
    };
  });

  constructor() {
    this.team.load();
    this.projectService
      .list({ size: 100 })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => this.projects.set(page?.contenido ?? []));

    // ?oportunidad=ID abre el detalle (por ejemplo, desde el simulador o una notificación).
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = Number(params.get('oportunidad'));
      if (id) this.openOpportunity(id);
    });

    effect(() => {
      const project = this.projectId();
      const text = this.text();
      this.reloadKey();
      if (!this.can.opportunities) return;
      untracked(() => {
        this.loadBoard(project, text);
        this.loadClosed(project);
      });
    });
    effect(() => {
      const q = { project: this.projectId(), text: this.text(), status: this.tableStatus(), stage: this.tableStage(), page: this.tablePage(), view: this.view(), key: this.reloadKey() };
      if (q.view === 'tabla' && this.can.opportunities) untracked(() => this.loadTable(q));
    });
  }

  selectProject(value: string): void {
    this.projectId.set(value ? Number(value) : null);
    this.tablePage.set(0);
  }

  loadBoard(project = this.projectId(), text = this.text()): void {
    this.boardLoading.set(true);
    this.boardError.set(null);
    forkJoin(
      STAGES.map((stage) =>
        this.crm.opportunities({ projectId: project, text, status: 'ABIERTA', stage: stage.value, size: PER_COLUMN }).pipe(
          map((page) => ({ stage, items: page.contenido, total: page.totalElementos })),
        ),
      ),
    ).subscribe({
      next: (columns) => {
        this.columns.set(columns);
        this.names.rememberOpportunities(columns.flatMap((c) => c.items));
        this.boardLoading.set(false);
      },
      error: (e) => {
        this.boardError.set(apiErrorMessage(e, 'No se pudo cargar el embudo.'));
        this.boardLoading.set(false);
      },
    });
  }

  loadTable(q = { project: this.projectId(), text: this.text(), status: this.tableStatus(), stage: this.tableStage(), page: this.tablePage() }): void {
    this.tableLoading.set(true);
    this.crm.opportunities({ projectId: q.project, text: q.text, status: q.status, stage: q.stage, page: q.page, size: 25 }).subscribe({
      next: (page) => {
        this.table.set(page);
        this.names.rememberOpportunities(page.contenido);
        this.tableLoading.set(false);
      },
      error: () => this.tableLoading.set(false),
    });
  }

  /** Ganadas y perdidas del mes en curso, para la tasa de cierre y los días al cierre. */
  private loadClosed(project: number | null): void {
    const month = currentPeriod();
    const inMonth = (o: Opportunity) => (o.fechaCierre ?? o.actualizadoEn).slice(0, 7) === month;
    forkJoin({
      won: this.crm.opportunities({ projectId: project, status: 'GANADA', size: 100 }).pipe(catchError(() => of(null))),
      lost: this.crm.opportunities({ projectId: project, status: 'PERDIDA', size: 100 }).pipe(catchError(() => of(null))),
    }).subscribe(({ won, lost }) =>
      this.closed.set({ won: (won?.contenido ?? []).filter(inMonth), lost: (lost?.contenido ?? []).filter(inMonth) }),
    );
  }

  showStageInTable(stage: OpportunityStage): void {
    this.tableStage.set(stage);
    this.tableStatus.set('ABIERTA');
    this.tablePage.set(0);
    this.view.set('tabla');
  }

  setView(value: string): void {
    this.view.set(value === 'tabla' ? 'tabla' : 'tablero');
  }

  openOpportunity(id: number): void {
    this.selectedId.set(id);
    this.drawerOpen.set(true);
  }

  refresh(): void {
    this.reloadKey.update((k) => k + 1);
  }

  stageLabel(o: Opportunity): string {
    return stageOf(o.etapaActual)?.label ?? o.etapaActual;
  }

  stageColor(o: Opportunity): string {
    return stageOf(o.etapaActual)?.color ?? '#8a96a9';
  }

  columnValue(c: Column): string {
    return formatCopCompact(c.items.reduce((s, o) => s + Number(o.valorEstimado ?? 0), 0));
  }

  initials(id: number | null): string {
    const name = this.team.name(id);
    return name
      .split(/\s+/)
      .slice(0, 2)
      .map((w) => w.charAt(0))
      .join('')
      .toUpperCase();
  }

  projectName(id: number): string {
    return this.projects().find((p) => p.id === id)?.nombre ?? '';
  }

  isLate(o: Opportunity): boolean {
    return !!o.fechaProximaAccion && new Date(o.fechaProximaAccion).getTime() < Date.now();
  }
}
