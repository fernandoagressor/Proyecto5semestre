import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { Project } from '../../core/models/empresa.models';
import { Goal, GoalProgress } from '../../core/models/gerencia.models';
import { AuthService } from '../../core/services/auth.service';
import { GerenciaService } from '../../core/services/gerencia.service';
import { TeamService } from '../../core/services/team.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import {
  apiErrorMessage,
  currentPeriod,
  formatCopCompact,
  formatPercent,
  NX_UI,
  periodLabel,
  ToastService,
} from '../../shared/ui';

interface GoalRow {
  goal: Goal;
  progress: GoalProgress | null;
  pct: number;
}

@Component({
  selector: 'app-goals-tab',
  imports: [...NX_UI, NgTemplateOutlet, ReactiveFormsModule, MoneyInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './goals-tab.component.html',
  styleUrl: './management.shared.scss',
})
export class GoalsTabComponent {
  private readonly api = inject(GerenciaService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  readonly team = inject(TeamService);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly projectId = input<number | null>(null);
  readonly projects = input<Project[]>([]);

  readonly period = signal(currentPeriod());
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly rows = signal<GoalRow[]>([]);
  readonly busy = signal<number | null>(null);
  readonly saving = signal(false);
  readonly editing = signal<Goal | null>(null);
  readonly editOpen = signal(false);

  readonly canManage = this.auth.hasAuthority('METAS_GESTIONAR');
  readonly canApprove = this.auth.hasAuthority('METAS_APROBAR');
  readonly periodLabel = computed(() => periodLabel(this.period()));

  readonly form = this.fb.group({
    asesorId: this.fb.control<string>(''),
    periodo: this.fb.control(currentPeriod(), [Validators.required, Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/)]),
    valorMeta: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    cantidadMeta: this.fb.control(0, [Validators.required, Validators.min(0)]),
    proyectoId: this.fb.control<string>(''),
  });
  readonly editForm = this.fb.group({
    valorMeta: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    cantidadMeta: this.fb.control(0, [Validators.required, Validators.min(0)]),
  });

  /** Metas por asesor (las que tienen asesor), de mayor a menor cumplimiento. */
  readonly advisorRows = computed(() =>
    this.rows()
      .filter((r) => r.goal.asesorId !== null)
      .map((r) => ({ ...r, name: this.team.name(r.goal.asesorId) }))
      .sort((a, b) => b.pct - a.pct),
  );
  /** Meta general de la empresa o del proyecto (sin asesor), si existe. */
  readonly overall = computed(() => this.rows().find((r) => r.goal.asesorId === null) ?? null);

  readonly kpis = computed(() => {
    const advisors = this.advisorRows();
    const overall = this.overall();
    const target = overall ? overall.goal.valorMeta : advisors.reduce((s, r) => s + r.goal.valorMeta, 0);
    const achieved = overall?.progress
      ? overall.progress.valorLogrado
      : advisors.reduce((s, r) => s + (r.progress?.valorLogrado ?? 0), 0);
    const contractsTarget = overall ? overall.goal.cantidadMeta : advisors.reduce((s, r) => s + r.goal.cantidadMeta, 0);
    const contracts = overall?.progress
      ? overall.progress.cantidadLograda
      : advisors.reduce((s, r) => s + (r.progress?.cantidadLograda ?? 0), 0);
    const above = advisors.filter((r) => r.pct >= 100);
    const pct = target > 0 ? (achieved / target) * 100 : 0;
    return {
      target: formatCopCompact(target),
      targetHint: advisors.length ? `Distribuida entre ${advisors.length} asesores` : 'Sin metas por asesor',
      achieved: formatCopCompact(achieved),
      achievedHint: `${formatPercent(pct)} de la meta`,
      pct,
      above: advisors.length ? `${above.length} de ${advisors.length}` : '—',
      aboveHint: above[0] ? `${above[0].name} · ${formatPercent(above[0].pct, 0)}` : 'Nadie supera aún su meta',
      contracts: `${contracts} / ${contractsTarget}`,
      projection: this.projection(achieved, target),
    };
  });

  constructor() {
    effect(() => {
      const project = this.projectId();
      const period = this.period();
      untracked(() => this.load(period, project));
    });
  }

  load(period = this.period(), project = this.projectId()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .goals({ periodo: period, proyectoId: project })
      .pipe(
        switchMap((page) =>
          page.contenido.length === 0
            ? of([] as GoalRow[])
            : forkJoin(
                page.contenido.map((goal) =>
                  this.api.goalProgress(goal.id).pipe(
                    catchError(() => of(null)),
                    map((progress) => this.row(goal, progress)),
                  ),
                ),
              ),
        ),
      )
      .subscribe({
        next: (rows) => {
          this.rows.set(rows);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(apiErrorMessage(e, 'No se pudieron cargar las metas.'));
          this.loading.set(false);
        },
      });
  }

  changePeriod(value: string): void {
    if (/^\d{4}-\d{2}$/.test(value)) this.period.set(value);
  }

  projectName(id: number | null): string {
    if (id === null) return 'Todos los proyectos';
    return this.projects().find((p) => p.id === id)?.nombre ?? `Proyecto #${id}`;
  }

  /** Ancho de la barra: 120 % de meta ocupa toda la barra; la marca del 100 % queda al 83 %. */
  barWidth(pct: number): number {
    return Math.min(pct, 120) / 1.2;
  }

  barTone(pct: number): string {
    return pct >= 100 ? 'success' : pct >= 70 ? '' : pct >= 40 ? 'warning' : 'danger';
  }

  money(value: number | null | undefined): string {
    return formatCopCompact(value ?? 0);
  }

  create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .createGoal({
        asesorId: v.asesorId ? Number(v.asesorId) : null,
        proyectoId: v.proyectoId ? Number(v.proyectoId) : null,
        periodo: v.periodo,
        valorMeta: v.valorMeta ?? 0,
        cantidadMeta: v.cantidadMeta,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.toast.success('Meta guardada en borrador. Falta aprobarla.');
          this.form.patchValue({ valorMeta: null, cantidadMeta: 0 });
          this.form.markAsUntouched();
          if (v.periodo !== this.period()) this.period.set(v.periodo);
          else this.load();
        },
        error: (e) => {
          this.saving.set(false);
          this.toast.error(apiErrorMessage(e, 'No se pudo guardar la meta.'));
        },
      });
  }

  openEdit(goal: Goal): void {
    this.editing.set(goal);
    this.editForm.setValue({ valorMeta: goal.valorMeta, cantidadMeta: goal.cantidadMeta });
    this.editOpen.set(true);
  }

  saveEdit(): void {
    const goal = this.editing();
    if (!goal || this.editForm.invalid) return;
    const v = this.editForm.getRawValue();
    this.saving.set(true);
    this.api.updateGoal(goal.id, v.valorMeta ?? 0, v.cantidadMeta).subscribe({
      next: () => {
        this.saving.set(false);
        this.editOpen.set(false);
        this.toast.success('Meta actualizada.');
        this.load();
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo actualizar la meta.'));
      },
    });
  }

  approve(goal: Goal): void {
    this.act(goal, this.api.approveGoal(goal.id), 'Meta aprobada.');
  }

  close(goal: Goal): void {
    this.act(goal, this.api.closeGoal(goal.id), 'Meta cerrada.');
  }

  private act(goal: Goal, request: ReturnType<GerenciaService['approveGoal']>, message: string): void {
    this.busy.set(goal.id);
    request.subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.success(message);
        this.load();
      },
      error: (e) => {
        this.busy.set(null);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }

  private row(goal: Goal, progress: GoalProgress | null): GoalRow {
    return { goal, progress, pct: Number(progress?.porcentajeValor ?? 0) };
  }

  /** Proyección lineal al cierre del mes, solo para el mes en curso. */
  private projection(achieved: number, target: number): { value: string; hint: string } {
    if (this.period() !== currentPeriod() || target <= 0) return { value: '—', hint: 'Solo para el mes en curso' };
    const now = new Date();
    const days = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
    const projected = (achieved / Math.max(now.getDate(), 1)) * days;
    return { value: formatCopCompact(projected), hint: `${formatPercent((projected / target) * 100)} de la meta` };
  }
}
