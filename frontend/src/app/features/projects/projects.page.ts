import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, of } from 'rxjs';

import { PageResponse } from '../../core/models/api.models';
import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { ProjectService } from '../../core/services/project.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';

@Component({
  selector: 'app-projects-page',
  imports: [ReactiveFormsModule, RouterLink, AppIconComponent],
  templateUrl: './projects.page.html',
  styleUrl: './projects.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProjectsPage {
  private readonly projectService = inject(ProjectService);
  readonly auth = inject(AuthService);

  readonly search = new FormControl('', { nonNullable: true });
  readonly selectedStatus = signal('');
  readonly page = signal<PageResponse<Project> | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly activatingProjectId = signal<number | null>(null);
  readonly feedback = signal('');
  readonly actionError = signal('');

  readonly statuses = [
    { value: '', label: 'Todos los estados' },
    { value: 'PLANEACION', label: 'Planeación' },
    { value: 'ACTIVO', label: 'Activo' },
    { value: 'PAUSADO', label: 'Pausado' },
    { value: 'FINALIZADO', label: 'Finalizado' },
    { value: 'INACTIVO', label: 'Inactivo' },
  ];

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.load(0));
    this.load();
  }

  changeStatus(status: string): void {
    this.selectedStatus.set(status);
    this.load(0);
  }

  load(pageNumber = this.page()?.pagina ?? 0): void {
    this.loading.set(true);
    this.error.set(false);
    this.projectService
      .list({
        text: this.search.value.trim(),
        status: this.selectedStatus(),
        page: pageNumber,
        size: 12,
      })
      .pipe(
        catchError(() => {
          this.error.set(true);
          return of(null);
        }),
        finalize(() => this.loading.set(false)),
      )
      .subscribe((page) => {
        if (page) this.page.set(page);
      });
  }

  statusLabel(status: Project['estado']): string {
    return this.statuses.find((item) => item.value === status)?.label ?? status;
  }

  progress(project: Project): number {
    return Math.min(100, Math.max(0, Number(project.porcentajeAvance ?? 0)));
  }

  canActivate(project: Project): boolean {
    return (
      project.estado === 'PLANEACION' ||
      project.estado === 'PAUSADO' ||
      project.estado === 'INACTIVO'
    );
  }

  activateProject(event: MouseEvent, project: Project): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.canActivate(project) || this.activatingProjectId() !== null) return;

    this.activatingProjectId.set(project.id);
    this.feedback.set('');
    this.actionError.set('');
    this.projectService
      .changeStatus(project.id, 'ACTIVO')
      .pipe(finalize(() => this.activatingProjectId.set(null)))
      .subscribe({
        next: (updated) => {
          const current = this.page();
          if (current) {
            this.page.set({
              ...current,
              contenido: current.contenido.map((item) => (item.id === updated.id ? updated : item)),
            });
          }
          this.feedback.set(
            `El proyecto ${updated.nombre} quedó activo y ya puede utilizarse en Ventas.`,
          );
        },
        error: (error) => {
          const response = error as {
            error?: { detail?: string; title?: string };
            message?: string;
          };
          this.actionError.set(
            response?.error?.detail ??
              response?.error?.title ??
              response?.message ??
              'No fue posible activar el proyecto.',
          );
        },
      });
  }
}
