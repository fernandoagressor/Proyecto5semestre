import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';

import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { ProjectService } from '../../core/services/project.service';
import { TeamService } from '../../core/services/team.service';
import { NX_UI, TabItem } from '../../shared/ui';
import { BudgetTabComponent } from './budget-tab.component';
import { CommissionsTabComponent } from './commissions-tab.component';
import { GoalsTabComponent } from './goals-tab.component';
import { ProfitabilityTabComponent } from './profitability-tab.component';

/**
 * Gerencia y control (vista "Finanzas-Comisiones" del diseño): metas por asesor, comisiones,
 * presupuesto contra ejecutado y rentabilidad por proyecto. Cada pestaña aparece solo con su
 * permiso; el proyecto elegido arriba filtra todas.
 */
@Component({
  selector: 'app-management-page',
  imports: [...NX_UI, GoalsTabComponent, CommissionsTabComponent, BudgetTabComponent, ProfitabilityTabComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="nx-page">
      <nx-page-header eyebrow="Gerencia y control" title="Metas, comisiones y presupuesto"
        description="Cumplimiento del equipo comercial, liquidación de comisiones y control de costos por proyecto.">
        <label class="nx-field project-filter">
          <span>Proyecto</span>
          <select class="nx-select" [value]="projectId() ?? ''" (change)="selectProject($any($event.target).value)">
            <option value="">Todos los proyectos</option>
            @for (p of projects(); track p.id) {
              <option [value]="p.id">{{ p.nombre }}</option>
            }
          </select>
        </label>
      </nx-page-header>

      @if (tabs().length === 0) {
        <div class="nx-card nx-card--pad">
          <nx-state [empty]="true" emptyIcon="lock" emptyTitle="Sin secciones disponibles"
            emptyText="Tu rol no tiene permisos para metas, comisiones, presupuestos ni rentabilidad." />
        </div>
      } @else {
        <nx-tabs [items]="tabs()" [(active)]="tab" label="Secciones de gerencia" />
        @switch (tab()) {
          @case ('metas') { <app-goals-tab [projectId]="projectId()" [projects]="projects()" /> }
          @case ('comisiones') { <app-commissions-tab [projectId]="projectId()" [projects]="projects()" /> }
          @case ('presupuesto') { <app-budget-tab [projectId]="projectId()" [projects]="projects()" /> }
          @case ('rentabilidad') { <app-profitability-tab [projectId]="projectId()" [projects]="projects()" /> }
        }
      }
    </div>
  `,
  styles: `
    .project-filter { min-width: 240px; }
  `,
})
export class ManagementPage {
  private readonly auth = inject(AuthService);
  private readonly projectService = inject(ProjectService);
  private readonly team = inject(TeamService);

  readonly projects = signal<Project[]>([]);
  readonly projectId = signal<number | null>(null);

  readonly tabs = computed<TabItem[]>(() => {
    const can = (...p: string[]) => this.auth.hasAny(p);
    const items: TabItem[] = [];
    if (can('METAS_VER')) items.push({ id: 'metas', label: 'Metas' });
    if (can('COMISIONES_EQUIPO_VER', 'COMISIONES_PROPIAS_VER')) items.push({ id: 'comisiones', label: 'Comisiones' });
    if (can('PRESUPUESTOS_VER', 'COSTOS_PROYECTO_VER')) items.push({ id: 'presupuesto', label: 'Presupuesto y costos' });
    if (can('RENTABILIDAD_VER')) items.push({ id: 'rentabilidad', label: 'Rentabilidad' });
    return items;
  });
  readonly tab = signal<string>(this.tabs()[0]?.id ?? 'metas');

  constructor() {
    this.team.load();
    this.projectService
      .list({ size: 100 })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => this.projects.set(page?.contenido ?? []));
  }

  selectProject(value: string): void {
    this.projectId.set(value ? Number(value) : null);
  }
}
