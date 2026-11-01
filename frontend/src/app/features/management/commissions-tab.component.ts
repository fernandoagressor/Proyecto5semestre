import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, concatMap, from, map, Observable, of, toArray } from 'rxjs';

import { Project } from '../../core/models/empresa.models';
import { Commission, CommissionRule, CommissionStatus } from '../../core/models/gerencia.models';
import { Contract } from '../../core/models/sales.models';
import { AuthService } from '../../core/services/auth.service';
import { GerenciaService } from '../../core/services/gerencia.service';
import { SalesService } from '../../core/services/sales.service';
import { TeamService } from '../../core/services/team.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, formatCop, formatCopCompact, formatDate, NX_UI, statusLabel, todayIso, ToastService } from '../../shared/ui';

type DateAction = 'causar' | 'pagar';

const STATUS_CARDS: { id: CommissionStatus; label: string; color: string }[] = [
  { id: 'PROYECTADA', label: 'Proyectadas', color: '#8a96a9' },
  { id: 'CAUSADA', label: 'Causadas por aprobar', color: '#d97706' },
  { id: 'APROBADA', label: 'Aprobadas por pagar', color: '#4f46e5' },
  { id: 'PAGADA', label: 'Pagadas', color: '#10a37f' },
];

export const CAUSATION_CONDITIONS = [
  { id: 'CONTRATO_FIRMADO', label: 'Al firmar el contrato' },
  { id: 'CONTRATO_VIGENTE', label: 'Con el contrato vigente' },
  { id: 'CUOTA_INICIAL_PAGADA', label: 'Al pagar la cuota inicial' },
  { id: 'RECAUDO_TOTAL', label: 'Al recaudar el total' },
];

@Component({
  selector: 'app-commissions-tab',
  imports: [...NX_UI, ReactiveFormsModule, MoneyInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './commissions-tab.component.html',
  styleUrl: './management.shared.scss',
})
export class CommissionsTabComponent {
  private readonly api = inject(GerenciaService);
  private readonly sales = inject(SalesService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly team = inject(TeamService);

  readonly projectId = input<number | null>(null);
  readonly projects = input<Project[]>([]);

  readonly statusCards = STATUS_CARDS;
  readonly conditions = CAUSATION_CONDITIONS;
  readonly canManage = this.auth.hasAuthority('COMISIONES_GESTIONAR');
  readonly canApprove = this.auth.hasAuthority('COMISIONES_APROBAR');
  readonly canRules = this.auth.hasAuthority('COMISIONES_REGLAS_GESTIONAR');
  readonly seesTeam = this.auth.hasAuthority('COMISIONES_EQUIPO_VER');

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly items = signal<Commission[]>([]);
  readonly total = signal(0);
  readonly rules = signal<CommissionRule[]>([]);
  readonly contracts = signal<Map<number, Contract>>(new Map());
  readonly filter = signal<CommissionStatus | ''>('CAUSADA');
  readonly selected = signal<Set<number>>(new Set());
  readonly working = signal(false);

  readonly dateAction = signal<DateAction | null>(null);
  readonly dateOpen = signal(false);
  readonly dateTargets = signal<Commission[]>([]);
  readonly actionDate = signal(todayIso());
  readonly ruleOpen = signal(false);
  readonly projectOpen = signal(false);

  readonly ruleForm = this.fb.group({
    nombre: this.fb.control('', [Validators.required, Validators.maxLength(140)]),
    proyectoId: this.fb.control(''),
    tipo: this.fb.control<'PORCENTAJE' | 'VALOR_FIJO'>('PORCENTAJE'),
    valor: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    condicionCausacion: this.fb.control('CONTRATO_FIRMADO', Validators.required),
    vigenteDesde: this.fb.control(todayIso(), Validators.required),
    vigenteHasta: this.fb.control(''),
  });
  readonly projectForm = this.fb.group({
    contratoId: this.fb.control('', Validators.required),
    reglaComisionId: this.fb.control('', Validators.required),
    baseCalculo: this.fb.control<number | null>(null),
  });

  readonly totals = computed(() =>
    STATUS_CARDS.map((card) => {
      const list = this.items().filter((c) => c.estado === card.id);
      return { ...card, count: list.length, value: formatCopCompact(list.reduce((s, c) => s + Number(c.valorComision), 0)) };
    }),
  );
  readonly visible = computed(() => {
    const f = this.filter();
    return this.items().filter((c) => (f ? c.estado === f : c.estado !== 'ANULADA'));
  });
  readonly selectedItems = computed(() => this.items().filter((c) => this.selected().has(c.id)));
  readonly selectedTotal = computed(() => formatCop(this.selectedItems().reduce((s, c) => s + Number(c.valorComision), 0)));
  readonly approvable = computed(() => this.selectedItems().filter((c) => c.estado === 'CAUSADA'));
  readonly payable = computed(() => this.items().filter((c) => c.estado === 'APROBADA'));
  readonly activeRules = computed(() => this.rules().filter((r) => r.activo));
  readonly filterLabel = computed(() => STATUS_CARDS.find((c) => c.id === this.filter())?.label.toLowerCase() ?? 'todas');
  readonly contractOptions = computed(() => [...this.contracts().values()].filter((c) => c.estado !== 'ANULADO'));

  constructor() {
    effect(() => {
      const project = this.projectId();
      untracked(() => this.load(project));
    });
  }

  load(project = this.projectId()): void {
    this.loading.set(true);
    this.error.set(null);
    this.selected.set(new Set());
    this.api.commissions({ proyectoId: project }).subscribe({
      next: (page) => {
        this.items.set(page.contenido);
        this.total.set(page.totalElementos);
        this.loading.set(false);
        this.loadContracts(project);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudieron cargar las comisiones.'));
        this.loading.set(false);
      },
    });
    this.api
      .rules({ proyectoId: project })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => this.rules.set(page?.contenido ?? []));
  }

  contractLabel(id: number): string {
    return this.contracts().get(id)?.codigo ?? `Contrato #${id}`;
  }

  ruleLabel(id: number): string {
    return this.rules().find((r) => r.id === id)?.nombre ?? `Regla #${id}`;
  }

  ruleValue(rule: CommissionRule): string {
    return rule.tipo === 'PORCENTAJE' ? `${Number(rule.valor).toLocaleString('es-CO')} %` : formatCop(rule.valor);
  }

  conditionLabel(id: string): string {
    return CAUSATION_CONDITIONS.find((c) => c.id === id)?.label ?? statusLabel(id);
  }

  projectName(id: number | null): string {
    if (id === null) return 'Todos';
    return this.projects().find((p) => p.id === id)?.nombre ?? `#${id}`;
  }

  date(value: string | null): string {
    return formatDate(value);
  }

  toggle(id: number, on: boolean): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  toggleAll(on: boolean): void {
    this.selected.set(new Set(on ? this.visible().map((c) => c.id) : []));
  }

  allSelected(): boolean {
    const v = this.visible();
    return v.length > 0 && v.every((c) => this.selected().has(c.id));
  }

  approveSelected(): void {
    const list = this.approvable();
    this.runBatch(list, (c) => this.api.approveCommission(c.id), 'aprobadas');
  }

  voidOne(c: Commission): void {
    this.runBatch([c], (x) => this.api.voidCommission(x.id), 'anuladas');
  }

  askDate(action: DateAction, targets: Commission[]): void {
    if (targets.length === 0) return;
    this.dateAction.set(action);
    this.dateTargets.set(targets);
    this.actionDate.set(todayIso());
    this.dateOpen.set(true);
  }

  confirmDate(): void {
    const action = this.dateAction();
    const date = this.actionDate();
    if (!action || !date) return;
    const call = action === 'causar' ? (c: Commission) => this.api.accrueCommission(c.id, date) : (c: Commission) => this.api.payCommission(c.id, date);
    this.runBatch(this.dateTargets(), call, action === 'causar' ? 'causadas' : 'pagadas', () => this.dateOpen.set(false));
  }

  saveRule(): void {
    if (this.ruleForm.invalid) {
      this.ruleForm.markAllAsTouched();
      return;
    }
    const v = this.ruleForm.getRawValue();
    this.working.set(true);
    this.api
      .createRule({
        nombre: v.nombre.trim(),
        proyectoId: v.proyectoId ? Number(v.proyectoId) : null,
        tipo: v.tipo,
        valor: v.valor ?? 0,
        condicionCausacion: v.condicionCausacion,
        vigenteDesde: v.vigenteDesde,
        vigenteHasta: v.vigenteHasta || null,
      })
      .subscribe({
        next: () => {
          this.working.set(false);
          this.ruleOpen.set(false);
          this.ruleForm.reset();
          this.toast.success('Regla de comisión creada.');
          this.load();
        },
        error: (e) => {
          this.working.set(false);
          this.toast.error(apiErrorMessage(e, 'No se pudo crear la regla.'));
        },
      });
  }

  closeRule(rule: CommissionRule): void {
    this.working.set(true);
    this.api.closeRule(rule.id, todayIso()).subscribe({
      next: () => {
        this.working.set(false);
        this.toast.success(`La regla "${rule.nombre}" quedó cerrada desde hoy.`);
        this.load();
      },
      error: (e) => {
        this.working.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo cerrar la regla.'));
      },
    });
  }

  openProjection(): void {
    this.projectForm.reset();
    this.projectOpen.set(true);
  }

  saveProjection(): void {
    if (this.projectForm.invalid) {
      this.projectForm.markAllAsTouched();
      return;
    }
    const v = this.projectForm.getRawValue();
    this.working.set(true);
    this.api
      .projectCommission(Number(v.contratoId), Number(v.reglaComisionId), v.baseCalculo)
      .subscribe({
        next: () => {
          this.working.set(false);
          this.projectOpen.set(false);
          this.toast.success('Comisión proyectada.');
          this.filter.set('PROYECTADA');
          this.load();
        },
        error: (e) => {
          this.working.set(false);
          this.toast.error(apiErrorMessage(e, 'No se pudo proyectar la comisión.'));
        },
      });
  }

  /** Aplica una acción a varias comisiones, una tras otra, y resume el resultado. */
  private runBatch(list: Commission[], call: (c: Commission) => Observable<Commission>, verb: string, done?: () => void): void {
    if (list.length === 0) return;
    this.working.set(true);
    from(list)
      .pipe(
        concatMap((c) => call(c).pipe(map(() => null), catchError((e) => of(apiErrorMessage(e))))),
        toArray(),
      )
      .subscribe((results) => {
        this.working.set(false);
        const failed = results.filter((r): r is string => r !== null);
        const ok = results.length - failed.length;
        if (ok > 0) this.toast.success(`${ok} ${ok === 1 ? 'comisión' : 'comisiones'} ${verb}.`);
        if (failed.length > 0) this.toast.error(`${failed.length} no se pudieron procesar: ${failed[0]}`);
        done?.();
        this.load();
      });
  }

  private loadContracts(project: number | null): void {
    if (!this.auth.hasAuthority('CONTRATOS_VER')) return;
    this.sales
      .contracts({ projectId: project, size: 100 })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => this.contracts.set(new Map((page?.contenido ?? []).map((c) => [c.id, c]))));
  }
}
