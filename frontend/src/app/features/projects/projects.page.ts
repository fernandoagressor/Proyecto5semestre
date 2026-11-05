import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, from, map, mergeMap, of } from 'rxjs';

import { CatalogImage, CatalogStatus } from '../../core/models/catalog.models';
import { Company, Project } from '../../core/models/empresa.models';
import { InventorySummary, ProjectStage } from '../../core/models/inventory.models';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { CompanyService } from '../../core/services/company.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectPayload, ProjectService } from '../../core/services/project.service';
import { apiErrorMessage, ChipOption, formatCopCompact, NX_UI, ToastService } from '../../shared/ui';

const STATUS_FILTERS: ChipOption[] = [
  { value: '', label: 'Todos' },
  { value: 'ACTIVO', label: 'Activos' },
  { value: 'PLANEACION', label: 'En planeación' },
  { value: 'PAUSADO', label: 'Pausados' },
  { value: 'FINALIZADO', label: 'Finalizados' },
];
const STATUSES: { value: Project['estado']; label: string }[] = [
  { value: 'PLANEACION', label: 'Planeación' },
  { value: 'ACTIVO', label: 'Activo' },
  { value: 'PAUSADO', label: 'Pausado' },
  { value: 'FINALIZADO', label: 'Finalizado' },
  { value: 'INACTIVO', label: 'Inactivo' },
];

/** Proyectos (vista "Proyectos" del diseño): tarjetas con avance de ventas y panel de detalle. */
@Component({
  selector: 'app-projects-page',
  imports: [...NX_UI, ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './projects.page.html',
  styleUrl: './projects.page.scss',
})
export class ProjectsPage {
  private readonly projectService = inject(ProjectService);
  private readonly inventory = inject(InventoryService);
  private readonly companyService = inject(CompanyService);
  private readonly toast = inject(ToastService);
  private readonly catalog = inject(CatalogService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);

  readonly canCreate = this.auth.hasAuthority('PROYECTOS_CREAR');
  readonly canEdit = this.auth.hasAuthority('PROYECTOS_EDITAR');
  readonly seesLots = this.auth.hasAuthority('LOTES_VER');
  readonly canManageStages = this.auth.hasAuthority('ETAPAS_GESTIONAR');
  readonly canConfigure = this.auth.hasAuthority('CONFIGURACION_EMPRESA_EDITAR');
  readonly catalogStatus = signal<CatalogStatus | null>(null);
  readonly catalogUrl = computed(() => {
    const c = this.catalogStatus();
    return c ? `/catalogo/${c.codigo}` : '';
  });
  readonly images = signal<CatalogImage[]>([]);
  readonly imageUrl = new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^https:\/\/\S+$/), Validators.maxLength(500)] });
  readonly imageTitle = new FormControl('', { nonNullable: true, validators: Validators.maxLength(160) });
  readonly filters = STATUS_FILTERS;
  readonly statuses = STATUSES;

  readonly search = new FormControl('', { nonNullable: true });
  readonly text = toSignal(this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()), { initialValue: '' });
  readonly status = signal('');
  readonly projects = signal<Project[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly company = signal<Company | null>(null);
  readonly summaries = signal<Record<number, InventorySummary>>({});

  readonly selected = signal<Project | null>(null);
  readonly stages = signal<ProjectStage[]>([]);
  readonly busy = signal(false);
  readonly formOpen = signal(false);
  readonly editing = signal<Project | null>(null);

  readonly limitLabel = computed(() => {
    const c = this.company();
    return c ? `${this.total()} de ${c.limiteProyectos} proyectos permitidos por tu plan` : `${this.total()} proyectos`;
  });
  readonly atLimit = computed(() => {
    const c = this.company();
    return !!c && this.total() >= c.limiteProyectos;
  });

  readonly form = this.fb.group({
    // Opcional: al crear se asigna solo (PRY-001…); al editar se puede cambiar.
    codigo: this.fb.control('', [Validators.maxLength(40)]),
    nombre: this.fb.control('', [Validators.required, Validators.maxLength(180)]),
    departamento: this.fb.control('', [Validators.required, Validators.maxLength(100)]),
    ciudad: this.fb.control('', [Validators.required, Validators.maxLength(100)]),
    direccion: this.fb.control('', Validators.maxLength(240)),
    fechaInicio: this.fb.control(''),
    fechaFinEstimada: this.fb.control(''),
    maxLotes: this.fb.control<number | null>(null, Validators.min(1)),
    imagenPrincipalUrl: this.fb.control('', Validators.maxLength(500)),
    descripcion: this.fb.control('', Validators.maxLength(10000)),
  });

  constructor() {
    if (this.auth.user()?.empresaId) {
      this.companyService.current().pipe(catchError(() => of(null))).subscribe((c) => this.company.set(c));
    }
    if (this.auth.user()?.empresaId) {
      this.catalog.status().pipe(catchError(() => of(null))).subscribe((st) => this.catalogStatus.set(st));
    }
    effect(() => {
      const text = this.text();
      const status = this.status();
      untracked(() => this.load(text, status));
    });
  }

  load(text = this.text(), status = this.status()): void {
    this.loading.set(true);
    this.error.set(null);
    this.projectService.list({ text, status, size: 60 }).subscribe({
      next: (page) => {
        this.projects.set(page.contenido);
        if (!text && !status) this.total.set(page.totalElementos);
        this.loading.set(false);
        this.loadSummaries(page.contenido);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudieron cargar los proyectos.'));
        this.loading.set(false);
      },
    });
  }

  /** Totales del portafolio con los resúmenes de inventario de los proyectos listados. */
  readonly portfolio = computed(() => {
    const listed = this.projects().map((p) => this.summaries()[p.id]).filter((s): s is InventorySummary => !!s);
    const sum = (key: 'total' | 'vendidos' | 'reservados' | 'disponibles' | 'valorDisponible') =>
      listed.reduce((acc, s) => acc + Number(s[key] ?? 0), 0);
    const total = sum('total');
    return {
      activos: this.projects().filter((p) => p.estado === 'ACTIVO').length,
      lotes: total,
      vendidos: sum('vendidos'),
      reservados: sum('reservados'),
      disponibles: sum('disponibles'),
      valorDisponible: sum('valorDisponible'),
      pctVendido: total ? Math.round((sum('vendidos') / total) * 1000) / 10 : 0,
    };
  });

  summary(id: number): InventorySummary | undefined {
    return this.summaries()[id];
  }

  soldPct(id: number): number {
    const s = this.summaries()[id];
    return s && s.total > 0 ? (s.vendidos / s.total) * 100 : 0;
  }

  resPct(id: number): number {
    const s = this.summaries()[id];
    return s && s.total > 0 ? (s.reservados / s.total) * 100 : 0;
  }

  money(v: number | undefined): string {
    return formatCopCompact(v ?? 0);
  }

  initials(name: string): string {
    return name
      .split(/\s+/)
      .filter((w) => w.length > 2)
      .slice(0, 2)
      .map((w) => w.charAt(0))
      .join('')
      .toUpperCase();
  }

  statusLabel(value: string): string {
    return STATUSES.find((s) => s.value === value)?.label ?? value;
  }

  open(project: Project): void {
    this.selected.set(project);
    this.stages.set([]);
    this.inventory.stages(project.id).pipe(catchError(() => of([] as ProjectStage[]))).subscribe((list) => this.stages.set(list));
    this.images.set([]);
    this.imageUrl.reset('');
    this.imageTitle.reset('');
    this.catalog.images(project.id).pipe(catchError(() => of([] as CatalogImage[]))).subscribe((list) => this.images.set(list));
  }

  toggleCatalog(): void {
    const current = this.catalogStatus();
    if (!current) return;
    this.catalog.setPublic(!current.publico).subscribe({
      next: (st) => {
        this.catalogStatus.set(st);
        this.toast.success(st.publico ? 'Catálogo público activado: comparta el enlace con sus clientes.' : 'Catálogo público desactivado.');
      },
      error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo cambiar el catálogo.')),
    });
  }

  addImage(project: Project): void {
    if (!this.imageUrl.value.trim() || this.imageUrl.invalid) {
      this.imageUrl.markAsTouched();
      return;
    }
    this.busy.set(true);
    this.catalog.addImage(project.id, this.imageUrl.value.trim(), this.imageTitle.value.trim() || null).subscribe({
      next: (img) => {
        this.busy.set(false);
        this.images.update((list) => [...list, img]);
        this.imageUrl.reset('');
        this.imageTitle.reset('');
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo agregar la foto.'));
      },
    });
  }

  removeImage(project: Project, image: CatalogImage): void {
    this.catalog.removeImage(project.id, image.id).subscribe({
      next: () => this.images.update((list) => list.filter((i) => i.id !== image.id)),
      error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo quitar la foto.')),
    });
  }

  changeStatus(project: Project, estado: Project['estado']): void {
    if (estado === project.estado) return;
    this.busy.set(true);
    this.projectService.changeStatus(project.id, estado).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.replace(updated);
        this.toast.success(`${updated.nombre}: ${this.statusLabel(updated.estado).toLowerCase()}.`);
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo cambiar el estado.'));
        this.selected.set({ ...project });
      },
    });
  }

  openForm(project: Project | null): void {
    this.editing.set(project);
    this.form.reset({
      codigo: project?.codigo ?? '',
      nombre: project?.nombre ?? '',
      departamento: project?.departamento ?? '',
      ciudad: project?.ciudad ?? '',
      direccion: project?.direccion ?? '',
      fechaInicio: project?.fechaInicio ?? '',
      fechaFinEstimada: project?.fechaFinEstimada ?? '',
      maxLotes: project?.maxLotes ?? null,
      imagenPrincipalUrl: project?.imagenPrincipalUrl ?? '',
      descripcion: project?.descripcion ?? '',
    });
    this.formOpen.set(true);
  }

  save(): void {
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const current = this.editing();
    const payload: ProjectPayload = {
      codigo: v.codigo.trim() || null,
      nombre: v.nombre.trim(),
      departamento: v.departamento.trim(),
      ciudad: v.ciudad.trim(),
      direccion: v.direccion.trim() || null,
      fechaInicio: v.fechaInicio || null,
      fechaFinEstimada: v.fechaFinEstimada || null,
      maxLotes: v.maxLotes,
      imagenPrincipalUrl: v.imagenPrincipalUrl.trim() || null,
      descripcion: v.descripcion.trim() || null,
      gerenteId: current?.gerenteId ?? null,
    };
    this.busy.set(true);
    (current ? this.projectService.update(current.id, payload) : this.projectService.create(payload)).subscribe({
      next: (p) => {
        this.busy.set(false);
        this.formOpen.set(false);
        this.toast.success(current ? 'Proyecto actualizado.' : `Proyecto ${p.nombre} creado en planeación.`);
        if (current) {
          this.replace(p);
        } else {
          this.load();
        }
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo guardar el proyecto.'));
      },
    });
  }

  private replace(updated: Project): void {
    this.projects.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
    if (this.selected()?.id === updated.id) this.selected.set(updated);
  }

  private loadSummaries(list: Project[]): void {
    if (!this.seesLots) return;
    const missing = list.filter((p) => !this.summaries()[p.id]).slice(0, 24);
    from(missing)
      .pipe(mergeMap((p) => this.inventory.summary(p.id).pipe(catchError(() => of(null)), map((summary) => ({ id: p.id, summary }))), 4))
      .subscribe(({ id, summary }) => {
        if (summary) this.summaries.update((all) => ({ ...all, [id]: summary }));
      });
  }
}
