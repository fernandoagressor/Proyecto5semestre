import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, forkJoin, merge, Observable, of } from 'rxjs';

import { PageResponse } from '../../core/models/api.models';
import { Project } from '../../core/models/empresa.models';
import {
  Block,
  InventorySummary,
  Lot,
  LotPriceHistory,
  LotStateHistory,
  LotStatus,
  ProjectStage,
  StageStatus,
} from '../../core/models/inventory.models';
import { AuthService } from '../../core/services/auth.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectService } from '../../core/services/project.service';
import { NX_UI } from '../../shared/ui';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';

type InventoryView = 'lotes' | 'estructura';
type InventoryPanel = 'lote' | 'etapa' | 'manzana' | 'detalle' | null;

@Component({
  selector: 'app-inventory-page',
  imports: [ReactiveFormsModule, RouterLink, ...NX_UI, MoneyInputDirective],
  templateUrl: './inventory.page.html',
  styleUrl: './inventory.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryPage {
  private readonly inventory = inject(InventoryService);
  private readonly projectsService = inject(ProjectService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly projects = signal<Project[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  readonly selectedProject = computed(() =>
    this.projects().find((project) => project.id === this.selectedProjectId()) ?? null,
  );
  readonly summary = signal<InventorySummary | null>(null);
  readonly stages = signal<ProjectStage[]>([]);
  readonly blocks = signal<Block[]>([]);
  readonly lots = signal<PageResponse<Lot> | null>(null);
  readonly selectedLot = signal<Lot | null>(null);
  readonly editingLot = signal<Lot | null>(null);
  readonly stateHistory = signal<LotStateHistory[]>([]);
  readonly priceHistory = signal<LotPriceHistory[]>([]);
  readonly view = signal<InventoryView>('lotes');
  /** Plano (todos los lotes de la selección como fichas) o tabla paginada. */
  readonly lotsView = signal<'plano' | 'tabla'>('plano');
  readonly panel = signal<InventoryPanel>(null);
  readonly loading = signal(true);
  readonly lotsLoading = signal(false);
  readonly submitting = signal(false);
  readonly error = signal(false);
  readonly feedback = signal('');

  readonly search = new FormControl('', { nonNullable: true });
  readonly stateFilter = signal('');
  readonly stageFilter = signal<number | null>(null);
  readonly blockFilter = signal<number | null>(null);

  readonly lotForm = this.fb.nonNullable.group({
    etapaId: [0, [Validators.required, Validators.min(1)]],
    manzanaId: [0, [Validators.required, Validators.min(1)]],
    codigo: ['', [Validators.required, Validators.maxLength(50)]],
    numero: ['', [Validators.required, Validators.maxLength(30)]],
    areaM2: [0, [Validators.required, Validators.min(0.01)]],
    frenteM: [0],
    fondoM: [0],
    valorLista: [0, [Validators.required, Validators.min(0)]],
    ubicacionReferencia: ['', Validators.maxLength(300)],
    descripcion: ['', Validators.maxLength(10000)],
  });
  readonly stageForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(30)]],
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    secuencia: [1, [Validators.required, Validators.min(1)]],
    fechaInicio: [''],
    fechaFin: [''],
    estado: ['ACTIVA' as StageStatus, Validators.required],
  });
  readonly blockForm = this.fb.nonNullable.group({
    etapaId: [0, [Validators.required, Validators.min(1)]],
    codigo: ['', [Validators.required, Validators.maxLength(30)]],
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    secuencia: [1, [Validators.required, Validators.min(1)]],
    estado: ['ACTIVA' as StageStatus, Validators.required],
  });
  readonly priceForm = this.fb.nonNullable.group({
    valor: [0, [Validators.required, Validators.min(0)]],
    motivo: ['', [Validators.required, Validators.maxLength(300)]],
  });
  readonly stateForm = this.fb.nonNullable.group({
    estado: ['BLOQUEADO' as LotStatus, Validators.required],
    motivo: ['', [Validators.required, Validators.maxLength(300)]],
  });

  readonly viewOptions = [
    { value: 'plano', label: 'Plano' },
    { value: 'tabla', label: 'Tabla' },
  ];

  readonly lotStatuses = [
    { value: '', label: 'Todos los estados' },
    { value: 'DISPONIBLE', label: 'Disponible' },
    { value: 'RESERVADO', label: 'Reservado' },
    { value: 'VENDIDO', label: 'Vendido' },
    { value: 'BLOQUEADO', label: 'Bloqueado' },
  ];

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.loadLots(0));
    merge(this.lotForm.controls.frenteM.valueChanges, this.lotForm.controls.fondoM.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.calculateLotArea());
    this.loadProjects();
  }

  selectProject(rawId: string): void {
    const id = Number(rawId) || null;
    this.selectedProjectId.set(id);
    this.stageFilter.set(null);
    this.blockFilter.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { proyecto: id },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.loadProject();
  }

  setLotsView(view: string): void {
    this.lotsView.set(view === 'tabla' ? 'tabla' : 'plano');
    this.loadLots(0);
  }

  /** Tarjetas de estado: filtran los lotes por estado (otra vez el mismo estado quita el filtro). */
  filterByState(state: string): void {
    this.changeStateFilter(this.stateFilter() === state ? '' : state);
  }

  selectView(view: InventoryView): void {
    this.view.set(view);
  }

  viewStageLots(stageId: number): void {
    this.changeStageFilter(`${stageId}`);
    this.selectView('lotes');
  }

  changeStateFilter(value: string): void {
    this.stateFilter.set(value);
    this.loadLots(0);
  }

  changeStageFilter(rawId: string): void {
    const id = Number(rawId) || null;
    this.stageFilter.set(id);
    this.blockFilter.set(null);
    this.blocks.set([]);
    if (id && this.auth.hasAuthority('MANZANAS_VER')) this.loadBlocks(id);
    this.loadLots(0);
  }

  changeBlockFilter(rawId: string): void {
    this.blockFilter.set(Number(rawId) || null);
    this.loadLots(0);
  }

  openCreateLot(): void {
    this.editingLot.set(null);
    this.lotForm.reset({
      etapaId: this.stageFilter() ?? 0,
      manzanaId: this.blockFilter() ?? 0,
      codigo: '', numero: '', areaM2: 0, frenteM: 0, fondoM: 0, valorLista: 0,
      ubicacionReferencia: '', descripcion: '',
    });
    if (this.lotForm.controls.etapaId.value) this.loadBlocks(this.lotForm.controls.etapaId.value);
    this.panel.set('lote');
  }

  openEditLot(lot: Lot): void {
    if (!this.auth.hasAuthority('LOTES_EDITAR')) return;
    if (lot.estado === 'RESERVADO' || lot.estado === 'VENDIDO') {
      this.feedback.set('Los lotes reservados o vendidos no permiten modificar sus datos físicos.');
      return;
    }

    this.selectedLot.set(lot);
    this.editingLot.set(lot);
    this.lotForm.reset({
      etapaId: lot.etapaId,
      manzanaId: lot.manzanaId,
      codigo: lot.codigo,
      numero: lot.numero,
      areaM2: Number(lot.areaM2),
      frenteM: Number(lot.frenteM ?? 0),
      fondoM: Number(lot.fondoM ?? 0),
      valorLista: Number(lot.valorLista),
      ubicacionReferencia: lot.ubicacionReferencia ?? '',
      descripcion: lot.descripcion ?? '',
    });
    this.loadBlocks(lot.etapaId);
    this.panel.set('lote');
  }

  openCreateStage(): void {
    this.stageForm.reset({ codigo: '', nombre: '', secuencia: this.stages().length + 1, fechaInicio: '', fechaFin: '', estado: 'ACTIVA' });
    this.panel.set('etapa');
  }

  openCreateBlock(stageId?: number): void {
    const id = stageId ?? this.stageFilter() ?? this.stages()[0]?.id ?? 0;
    this.blockForm.reset({ etapaId: id, codigo: '', nombre: '', secuencia: this.blocks().length + 1, estado: 'ACTIVA' });
    this.panel.set('manzana');
  }

  closePanel(): void {
    this.panel.set(null);
    this.selectedLot.set(null);
    this.editingLot.set(null);
    this.stateHistory.set([]);
    this.priceHistory.set([]);
  }

  lotStageChanged(rawId: string): void {
    const id = Number(rawId) || 0;
    this.lotForm.controls.etapaId.setValue(id);
    this.lotForm.controls.manzanaId.setValue(0);
    this.blocks.set([]);
    if (id) this.loadBlocks(id);
  }

  showLot(lot: Lot): void {
    const projectId = this.selectedProjectId();
    if (!projectId) return;
    this.selectedLot.set(lot);
    this.panel.set('detalle');
    this.priceForm.reset({ valor: Number(lot.valorLista), motivo: '' });
    this.stateForm.reset({ estado: lot.estado === 'BLOQUEADO' ? 'DISPONIBLE' : 'BLOQUEADO', motivo: '' });
    forkJoin({
      states: this.inventory.stateHistory(projectId, lot.id).pipe(catchError(() => of([]))),
      prices: this.inventory.priceHistory(projectId, lot.id).pipe(catchError(() => of([]))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ states, prices }) => {
        this.stateHistory.set(states);
        this.priceHistory.set(prices);
      });
  }

  submitLot(): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.lotForm.invalid || this.submitting()) {
      this.lotForm.markAllAsTouched();
      return;
    }
    const value = this.lotForm.getRawValue();
    const details = {
      codigo: value.codigo.trim(),
      numero: value.numero.trim(),
      areaM2: Number(value.areaM2),
      frenteM: value.frenteM || null,
      fondoM: value.fondoM || null,
      ubicacionReferencia: value.ubicacionReferencia.trim() || null,
      descripcion: value.descripcion.trim() || null,
    };
    const editing = this.editingLot();

    if (editing) {
      this.runMutation(
        this.inventory.updateLot(projectId, editing.id, details),
        'Lote actualizado correctamente.',
      );
      return;
    }

    this.runMutation(
      this.inventory.createLot(projectId, {
        ...details,
        etapaId: value.etapaId,
        manzanaId: value.manzanaId,
        valorLista: Number(value.valorLista),
      }),
      'Lote creado correctamente.',
    );
  }

  submitStage(): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.stageForm.invalid || this.submitting()) {
      this.stageForm.markAllAsTouched();
      return;
    }
    const value = this.stageForm.getRawValue();
    this.runMutation(
      this.inventory.createStage(projectId, { ...value, fechaInicio: value.fechaInicio || null, fechaFin: value.fechaFin || null }),
      'Etapa creada correctamente.',
    );
  }

  submitBlock(): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.blockForm.invalid || this.submitting()) {
      this.blockForm.markAllAsTouched();
      return;
    }
    const { etapaId, ...payload } = this.blockForm.getRawValue();
    this.runMutation(this.inventory.createBlock(projectId, etapaId, payload), 'Manzana creada correctamente.');
  }

  submitPrice(): void {
    const projectId = this.selectedProjectId();
    const lot = this.selectedLot();
    if (!projectId || !lot || this.priceForm.invalid || this.submitting()) {
      this.priceForm.markAllAsTouched();
      return;
    }
    const value = this.priceForm.getRawValue();
    this.runMutation(this.inventory.changeLotPrice(projectId, lot.id, value.valor, value.motivo.trim()), 'Precio actualizado con trazabilidad.');
  }

  submitState(): void {
    const projectId = this.selectedProjectId();
    const lot = this.selectedLot();
    if (!projectId || !lot || this.stateForm.invalid || this.submitting()) {
      this.stateForm.markAllAsTouched();
      return;
    }
    const value = this.stateForm.getRawValue();
    this.runMutation(this.inventory.changeLotState(projectId, lot.id, value.estado, value.motivo.trim()), 'Estado del lote actualizado.');
  }

  loadLots(page = this.lots()?.pagina ?? 0): void {
    const projectId = this.selectedProjectId();
    if (!projectId) return;
    this.lotsLoading.set(true);
    this.error.set(false);
    this.inventory
      .listLots({
        projectId,
        state: this.stateFilter(),
        stageId: this.stageFilter(),
        blockId: this.blockFilter(),
        text: this.search.value.trim(),
        page,
        size: this.lotsView() === 'plano' ? 100 : 20,
      })
      .pipe(
        catchError(() => {
          this.error.set(true);
          return of(null);
        }),
        finalize(() => this.lotsLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((lots) => {
        if (lots) this.lots.set(lots);
      });
  }

  currency(value: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(Number(value ?? 0));
  }

  statusLabel(status: string): string {
    return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
  }

  stageName(id: number): string {
    return this.stages().find((stage) => stage.id === id)?.nombre ?? `Etapa #${id}`;
  }

  blockName(id: number): string {
    return this.blocks().find((block) => block.id === id)?.nombre ?? `Manzana #${id}`;
  }

  private calculateLotArea(): void {
    const front = Number(this.lotForm.controls.frenteM.value);
    const depth = Number(this.lotForm.controls.fondoM.value);
    if (front <= 0 || depth <= 0) return;

    const area = Math.round((front * depth + Number.EPSILON) * 100) / 100;
    this.lotForm.controls.areaM2.setValue(area, { emitEvent: false });
  }

  private loadProjects(): void {
    this.loading.set(true);
    this.projectsService
      .list({ size: 100 })
      .pipe(
        catchError(() => {
          this.error.set(true);
          return of(null);
        }),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        if (!page) return;
        this.projects.set(page.contenido);
        const requested = Number(this.route.snapshot.queryParamMap.get('proyecto'));
        const selected = page.contenido.some((project) => project.id === requested) ? requested : page.contenido[0]?.id;
        this.selectedProjectId.set(selected ?? null);
        if (selected) this.loadProject();
      });
  }

  private loadProject(): void {
    const projectId = this.selectedProjectId();
    if (!projectId) return;
    this.error.set(false);
    const stagesRequest = this.auth.hasAuthority('ETAPAS_VER') ? this.inventory.stages(projectId) : of([]);
    forkJoin({ summary: this.inventory.summary(projectId), stages: stagesRequest })
      .pipe(
        catchError(() => {
          this.error.set(true);
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((data) => {
        if (!data) return;
        this.summary.set(data.summary);
        this.stages.set(data.stages);
        this.loadLots(0);
      });
  }

  private loadBlocks(stageId: number): void {
    const projectId = this.selectedProjectId();
    if (!projectId || !this.auth.hasAuthority('MANZANAS_VER')) return;
    this.inventory
      .blocks(projectId, stageId)
      .pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe((blocks) => this.blocks.set(blocks));
  }

  private runMutation(request: Observable<unknown>, message: string): void {
    this.submitting.set(true);
    this.feedback.set('');
    request
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.feedback.set(message);
          this.closePanel();
          this.loadProject();
        },
        error: () => this.feedback.set('No fue posible completar la operación. Revisa los datos e inténtalo de nuevo.'),
      });
  }
}
