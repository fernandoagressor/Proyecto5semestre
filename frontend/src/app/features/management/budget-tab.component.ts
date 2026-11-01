import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';

import { PageResponse } from '../../core/models/api.models';
import { Project } from '../../core/models/empresa.models';
import { Budget, Cost, CostCategory, CostCategoryType } from '../../core/models/gerencia.models';
import { AuthService } from '../../core/services/auth.service';
import { GerenciaService } from '../../core/services/gerencia.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, formatCopCompact, formatDate, formatPercent, NX_UI, todayIso, ToastService } from '../../shared/ui';

interface BudgetLine {
  category: CostCategory;
  budget: number;
  spent: number;
  pct: number;
  tone: 'success' | 'warning' | 'danger';
}

const emptyPage = <T>(): PageResponse<T> => ({ contenido: [], pagina: 0, tamano: 0, totalElementos: 0, totalPaginas: 0, primera: true, ultima: true });

@Component({
  selector: 'app-budget-tab',
  imports: [...NX_UI, ReactiveFormsModule, MoneyInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './budget-tab.component.html',
  styleUrl: './management.shared.scss',
})
export class BudgetTabComponent {
  private readonly api = inject(GerenciaService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly projectId = input<number | null>(null);
  readonly projects = input<Project[]>([]);

  readonly seesBudgets = this.auth.hasAuthority('PRESUPUESTOS_VER');
  readonly seesCosts = this.auth.hasAuthority('COSTOS_PROYECTO_VER');
  readonly canManage = this.auth.hasAuthority('COSTOS_PROYECTO_GESTIONAR');
  readonly canApprove = this.auth.hasAuthority('PRESUPUESTOS_APROBAR');

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly categories = signal<CostCategory[]>([]);
  readonly budgets = signal<Budget[]>([]);
  readonly costs = signal<Cost[]>([]);
  readonly busy = signal(false);
  readonly budgetOpen = signal(false);
  readonly costOpen = signal(false);
  readonly categoriesOpen = signal(false);

  readonly budgetForm = this.fb.group({
    proyectoId: this.fb.control('', Validators.required),
    categoriaCostoId: this.fb.control('', Validators.required),
    periodoDesde: this.fb.control(`${new Date().getFullYear()}-01-01`, Validators.required),
    periodoHasta: this.fb.control(`${new Date().getFullYear()}-12-31`, Validators.required),
    valorPresupuestado: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
  });
  readonly costForm = this.fb.group({
    proyectoId: this.fb.control('', Validators.required),
    categoriaCostoId: this.fb.control('', Validators.required),
    fechaCosto: this.fb.control(todayIso(), Validators.required),
    concepto: this.fb.control('', [Validators.required, Validators.maxLength(220)]),
    proveedor: this.fb.control('', Validators.maxLength(180)),
    referencia: this.fb.control('', Validators.maxLength(100)),
    valor: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
  });
  readonly categoryForm = this.fb.group({
    codigo: this.fb.control('', [Validators.required, Validators.maxLength(40)]),
    nombre: this.fb.control('', [Validators.required, Validators.maxLength(140)]),
    tipo: this.fb.control<CostCategoryType>('DIRECTO'),
  });

  readonly activeCategories = computed(() => this.categories().filter((c) => c.activo));
  readonly pendingCosts = computed(() => this.costs().filter((c) => c.estado === 'REGISTRADO'));
  readonly recentCosts = computed(() => this.costs().filter((c) => c.estado !== 'REGISTRADO').slice(0, 15));

  /** Presupuesto aprobado contra costos aprobados o pagados, por categoría. */
  readonly lines = computed<BudgetLine[]>(() => {
    const budgets = this.budgets().filter((b) => b.estado === 'APROBADO' || b.estado === 'CERRADO');
    const costs = this.costs().filter((c) => c.estado === 'APROBADO' || c.estado === 'PAGADO');
    return this.categories()
      .map((category) => {
        const budget = budgets.filter((b) => b.categoriaCostoId === category.id).reduce((s, b) => s + Number(b.valorPresupuestado), 0);
        const spent = costs.filter((c) => c.categoriaCostoId === category.id).reduce((s, c) => s + Number(c.valor), 0);
        const pct = budget > 0 ? (spent / budget) * 100 : spent > 0 ? 100 : 0;
        const tone: BudgetLine['tone'] = pct > 100 ? 'danger' : pct > 90 ? 'warning' : 'success';
        return { category, budget, spent, pct, tone };
      })
      .filter((l) => l.budget > 0 || l.spent > 0);
  });
  readonly totals = computed(() => {
    const budget = this.lines().reduce((s, l) => s + l.budget, 0);
    const spent = this.lines().reduce((s, l) => s + l.spent, 0);
    return {
      budget: formatCopCompact(budget),
      spent: formatCopCompact(spent),
      pct: budget > 0 ? (spent / budget) * 100 : 0,
      available: formatCopCompact(budget - spent),
      pending: formatCopCompact(this.pendingCosts().reduce((s, c) => s + Number(c.valor), 0)),
    };
  });

  constructor() {
    effect(() => {
      const project = this.projectId();
      untracked(() => {
        this.load(project);
        if (project) {
          this.budgetForm.patchValue({ proyectoId: String(project) });
          this.costForm.patchValue({ proyectoId: String(project) });
        }
      });
    });
  }

  load(project = this.projectId()): void {
    this.loading.set(true);
    this.error.set(null);
    const budgets$: Observable<PageResponse<Budget>> = this.seesBudgets ? this.api.budgets({ proyectoId: project }) : of(emptyPage<Budget>());
    const costs$: Observable<PageResponse<Cost>> = this.seesCosts ? this.api.costs({ proyectoId: project }) : of(emptyPage<Cost>());
    forkJoin({ categories: this.api.categories(), budgets: budgets$, costs: costs$ }).subscribe({
      next: ({ categories, budgets, costs }) => {
        this.categories.set(categories);
        this.budgets.set(budgets.contenido);
        this.costs.set(costs.contenido);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudo cargar el presupuesto.'));
        this.loading.set(false);
      },
    });
  }

  categoryName(id: number): string {
    return this.categories().find((c) => c.id === id)?.nombre ?? `Categoría #${id}`;
  }

  projectName(id: number): string {
    return this.projects().find((p) => p.id === id)?.nombre ?? `Proyecto #${id}`;
  }

  money(value: number): string {
    return formatCopCompact(value);
  }

  pct(value: number): string {
    return formatPercent(value, 0);
  }

  date(value: string): string {
    return formatDate(value);
  }

  budgetAction(b: Budget, action: 'aprobar' | 'cerrar' | 'anular'): void {
    const verbs = { aprobar: 'aprobado', cerrar: 'cerrado', anular: 'anulado' };
    this.run(this.api.budgetAction(b.id, action), `Presupuesto ${verbs[action]}.`);
  }

  costAction(c: Cost, action: 'aprobar' | 'pagar' | 'anular'): void {
    const verbs = { aprobar: 'aprobado', pagar: 'marcado como pagado', anular: 'anulado' };
    this.run(this.api.costAction(c.id, action), `Costo ${verbs[action]}.`);
  }

  saveBudget(): void {
    if (this.budgetForm.invalid) return this.budgetForm.markAllAsTouched();
    const v = this.budgetForm.getRawValue();
    this.run(
      this.api.createBudget({
        proyectoId: Number(v.proyectoId),
        categoriaCostoId: Number(v.categoriaCostoId),
        periodoDesde: v.periodoDesde,
        periodoHasta: v.periodoHasta,
        valorPresupuestado: v.valorPresupuestado ?? 0,
      }),
      'Presupuesto creado en borrador.',
      () => {
        this.budgetOpen.set(false);
        this.budgetForm.patchValue({ valorPresupuestado: null, categoriaCostoId: '' });
      },
    );
  }

  saveCost(): void {
    if (this.costForm.invalid) return this.costForm.markAllAsTouched();
    const v = this.costForm.getRawValue();
    this.run(
      this.api.createCost({
        proyectoId: Number(v.proyectoId),
        categoriaCostoId: Number(v.categoriaCostoId),
        fechaCosto: v.fechaCosto,
        concepto: v.concepto.trim(),
        proveedor: v.proveedor.trim() || null,
        referencia: v.referencia.trim() || null,
        valor: v.valor ?? 0,
      }),
      'Costo registrado. Queda pendiente de aprobación.',
      () => {
        this.costOpen.set(false);
        this.costForm.patchValue({ concepto: '', proveedor: '', referencia: '', valor: null });
      },
    );
  }

  saveCategory(): void {
    if (this.categoryForm.invalid) return this.categoryForm.markAllAsTouched();
    const v = this.categoryForm.getRawValue();
    this.run(this.api.createCategory(v.codigo.trim().toUpperCase(), v.nombre.trim(), v.tipo), 'Categoría creada.', () =>
      this.categoryForm.reset({ codigo: '', nombre: '', tipo: 'DIRECTO' }),
    );
  }

  deactivateCategory(c: CostCategory): void {
    this.run(this.api.deactivateCategory(c.id), `Categoría "${c.nombre}" inactivada.`);
  }

  private run(request: Observable<unknown>, message: string, done?: () => void): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(message);
        done?.();
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }
}

