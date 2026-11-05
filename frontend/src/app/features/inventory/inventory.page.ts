import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, forkJoin, map, merge, Observable, of, switchMap } from 'rxjs';

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
import { aplicarErroresDelServidor, apiErrorMessage, NX_UI } from '../../shared/ui';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { LotConsecutive, nextLotConsecutive } from './lot-consecutive';
import { LotReserved, ReserveLotComponent } from './reserve-lot.component';
import { Reservation } from '../../core/models/sales.models';
import { SalesService } from '../../core/services/sales.service';

type InventoryView = 'lotes' | 'estructura';
type InventoryPanel = 'lote' | 'etapa' | 'manzana' | 'detalle' | 'cantidad' | 'reservar' | null;
/** Qué se genera con el diálogo de cantidad: manzanas de una etapa o lotes de una manzana. */
type QuantityTarget = { mode: 'manzanas'; stage: ProjectStage } | { mode: 'lotes'; stage: ProjectStage; block: Block };

@Component({
  selector: 'app-inventory-page',
  imports: [ReactiveFormsModule, RouterLink, ...NX_UI, MoneyInputDirective, ReserveLotComponent],
  templateUrl: './inventory.page.html',
  styleUrl: './inventory.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryPage {
  private readonly inventory = inject(InventoryService);
  private readonly sales = inject(SalesService);
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
  /** Todas las manzanas del proyecto, para rotular el plano aunque no haya etapa elegida. */
  readonly allBlocks = signal<Block[]>([]);
  /** Plano agrupado por manzana, en el orden de la etapa y la manzana. */
  readonly planGroups = computed(() => {
    const groups = new Map<number, Lot[]>();
    for (const lot of this.lots()?.contenido ?? []) {
      const list = groups.get(lot.manzanaId) ?? [];
      list.push(lot);
      groups.set(lot.manzanaId, list);
    }
    const order = (id: number) => this.allBlocks().findIndex((b) => b.id === id);
    return [...groups.entries()]
      .sort(([a], [b]) => order(a) - order(b) || a - b)
      .map(([blockId, lots]) => ({
        blockId,
        title: `${this.stageName(lots[0].etapaId)} · ${this.blockName(blockId)}`,
        lots: [...lots].sort((x, y) => x.numero.localeCompare(y.numero, 'es', { numeric: true })),
      }));
  });
  readonly lots = signal<PageResponse<Lot> | null>(null);
  /** Todos los lotes del proyecto (sin filtros): avance por etapa y consecutivo de lotes nuevos. */
  readonly allLots = signal<Lot[]>([]);
  /** Último consecutivo sugerido; solo se reemplaza lo que el usuario no ha escrito a mano. */
  readonly suggestedConsecutive = signal<LotConsecutive | null>(null);
  readonly selectedLot = signal<Lot | null>(null);
  readonly editingLot = signal<Lot | null>(null);
  /** Manzana elegida en el filtro de la estructura: la que se edita con "Editar manzana". */
  readonly selectedBlock = computed(() => this.blocks().find((b) => b.id === this.blockFilter()) ?? null);
  readonly editingStage = signal<ProjectStage | null>(null);
  readonly editingBlock = signal<Block | null>(null);
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
  /** Error del último guardado, dentro del panel abierto (para no tener que cerrarlo para leerlo). */
  readonly formError = signal('');
  /** Destino del diálogo "¿cuántas manzanas / cuántos lotes?". */
  readonly quantityTarget = signal<QuantityTarget | null>(null);
  readonly canGenerateLots = this.auth.hasAuthority('LOTES_CREAR');
  readonly canEditLots = this.auth.hasAuthority('LOTES_EDITAR');
  /** Reservar desde el lote usa el alta rápida, que pide todos estos permisos. */
  readonly canReserve = ['CLIENTES_CREAR', 'PROSPECTOS_CREAR', 'PROSPECTOS_CALIFICAR', 'OPORTUNIDADES_CREAR', 'OPORTUNIDADES_EDITAR', 'RESERVAS_CREAR']
    .every((permiso) => this.auth.hasAuthority(permiso));
  /** Reserva vigente del lote abierto en el detalle (si está reservado). */
  readonly lotReservation = signal<Reservation | null>(null);
  /** Lotes de cada manzana (todos y por completar), con todos los lotes del proyecto. */
  readonly lotCountByBlock = computed(() => {
    const counts = new Map<number, { total: number; pendientes: number }>();
    for (const lot of this.allLots()) {
      const c = counts.get(lot.manzanaId) ?? { total: 0, pendientes: 0 };
      c.total++;
      if (lot.estado === 'PENDIENTE') c.pendientes++;
      counts.set(lot.manzanaId, c);
    }
    return counts;
  });
  /** Siguiente lote por completar de la misma manzana (para "Guardar y seguir"). */
  readonly nextPendingLot = computed(() => {
    const current = this.editingLot();
    if (!current) return null;
    return this.allLots()
      .filter((lot) => lot.manzanaId === current.manzanaId && lot.estado === 'PENDIENTE' && lot.id !== current.id)
      .sort((a, b) => a.numero.localeCompare(b.numero, 'es', { numeric: true }))[0] ?? null;
  });

  readonly search = new FormControl('', { nonNullable: true });
  /** Rango de precio de lista; el API ya filtra por valor mínimo y máximo. */
  readonly minPrice = new FormControl<number | null>(null);
  readonly maxPrice = new FormControl<number | null>(null);
  readonly hasFilters = computed(() => !!(this.stateFilter() || this.stageFilter() || this.blockFilter()));
  readonly stateFilter = signal('');
  readonly stageFilter = signal<number | null>(null);
  readonly blockFilter = signal<number | null>(null);

  readonly lotForm = this.fb.nonNullable.group({
    etapaId: [0, [Validators.required, Validators.min(1)]],
    manzanaId: [0, [Validators.required, Validators.min(1)]],
    codigo: ['', [Validators.required, Validators.maxLength(50)]],
    numero: ['', [Validators.required, Validators.maxLength(30)]],
    areaM2: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    frenteM: [0, Validators.min(0)],
    fondoM: [0, Validators.min(0)],
    valorLista: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    ubicacionReferencia: ['', Validators.maxLength(300)],
    descripcion: ['', Validators.maxLength(10000)],
  });
  readonly stageForm = this.fb.nonNullable.group({
    codigo: ['', Validators.maxLength(30)],
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    fechaInicio: [''],
    fechaFin: [''],
    estado: ['ACTIVA' as StageStatus, Validators.required],
    cantidadManzanas: [10, [Validators.min(0), Validators.max(100)]],
  }, { validators: (group: AbstractControl) => {
    const inicio = group.get('fechaInicio')?.value;
    const fin = group.get('fechaFin')?.value;
    const error = inicio && fin && fin < inicio ? { server: 'El cierre no puede ser antes del inicio.' } : null;
    group.get('fechaFin')?.setErrors(error);
    return null;
  } });
  readonly blockForm = this.fb.nonNullable.group({
    etapaId: [0, [Validators.required, Validators.min(1)]],
    codigo: ['', [Validators.required, Validators.maxLength(30)]],
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    estado: ['ACTIVA' as StageStatus, Validators.required],
  });
  readonly quantityForm = this.fb.nonNullable.group({
    cantidad: [20, [Validators.required, Validators.min(1), Validators.max(500)]],
  });
  readonly priceForm = this.fb.nonNullable.group({
    valor: [0, [Validators.required, Validators.min(0)]],
    motivo: ['', [Validators.required, Validators.maxLength(300)]],
  });
  readonly stateForm = this.fb.nonNullable.group({
    estado: ['BLOQUEADO' as LotStatus, Validators.required],
    motivo: ['', [Validators.required, Validators.maxLength(300)]],
  });

  /** Participación de cada estado sobre el total del proyecto, para las tarjetas. */
  readonly shareOf = (count: number | undefined): string => {
    const total = this.summary()?.total ?? 0;
    return total ? `${Math.round(((count ?? 0) / total) * 1000) / 10} % del total` : 'Sin lotes';
  };
  readonly soldPct = computed(() => {
    const s = this.summary();
    return s?.total ? Math.round((s.vendidos / s.total) * 1000) / 10 : 0;
  });
  /** Fin del tramo de reservados en el anillo (vendidos + reservados). */
  readonly soldAndReservedPct = computed(() => {
    const s = this.summary();
    return s?.total ? Math.round(((s.vendidos + s.reservados) / s.total) * 1000) / 10 : 0;
  });
  /** Avance de ventas por etapa, calculado con todos los lotes del proyecto. */
  readonly stageProgress = computed(() =>
    this.stages()
      .map((stage) => {
        const lots = this.allLots().filter((lot) => lot.etapaId === stage.id);
        const count = (state: LotStatus) => lots.filter((lot) => lot.estado === state).length;
        const vendidos = count('VENDIDO');
        const total = lots.length;
        return {
          id: stage.id,
          nombre: stage.nombre,
          total,
          vendidos,
          reservados: count('RESERVADO'),
          disponibles: count('DISPONIBLE'),
          bloqueados: count('BLOQUEADO'),
          pct: total ? Math.round((vendidos / total) * 1000) / 10 : 0,
        };
      })
      .filter((stage) => stage.total > 0),
  );

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
    merge(this.search.valueChanges, this.minPrice.valueChanges, this.maxPrice.valueChanges)
      .pipe(debounceTime(350), takeUntilDestroyed())
      .subscribe(() => this.loadLots(0));
    merge(this.lotForm.controls.frenteM.valueChanges, this.lotForm.controls.fondoM.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.calculateLotArea());
    this.lotForm.controls.manzanaId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((blockId) => this.suggestConsecutive(Number(blockId)));
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

  /** Quita estado, etapa, manzana, búsqueda y rango de precio de una vez. */
  clearFilters(): void {
    this.stateFilter.set('');
    this.stageFilter.set(null);
    this.blockFilter.set(null);
    this.blocks.set([]);
    this.search.setValue('', { emitEvent: false });
    this.minPrice.setValue(null, { emitEvent: false });
    this.maxPrice.setValue(null, { emitEvent: false });
    this.loadLots(0);
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
    this.suggestedConsecutive.set(null);
    this.lotForm.reset({
      etapaId: this.stageFilter() ?? 0,
      manzanaId: this.blockFilter() ?? 0,
      codigo: '', numero: '', areaM2: null, frenteM: 0, fondoM: 0, valorLista: null,
      ubicacionReferencia: '', descripcion: '',
    });
    if (this.lotForm.controls.etapaId.value) this.loadBlocks(this.lotForm.controls.etapaId.value);
    this.suggestConsecutive(this.lotForm.controls.manzanaId.value);
    this.panel.set('lote');
  }

  /**
   * Llena número y código con el siguiente consecutivo de la manzana elegida. Solo actúa al crear,
   * y respeta lo que el usuario haya escrito a mano en cualquiera de los dos campos.
   */
  private suggestConsecutive(blockId: number): void {
    if (this.editingLot() || !blockId) return;
    const block = this.blocks().find((b) => b.id === blockId) ?? this.allBlocks().find((b) => b.id === blockId);
    const stage = this.stages().find((s) => s.id === (block?.etapaId ?? this.lotForm.controls.etapaId.value));
    const next = nextLotConsecutive(
      this.allLots().filter((lot) => lot.manzanaId === blockId),
      stage?.codigo ?? '',
      block?.codigo ?? '',
    );
    const previous = this.suggestedConsecutive();
    const { codigo, numero } = this.lotForm.controls;
    if (!codigo.value || codigo.value === previous?.codigo) codigo.setValue(next.codigo);
    if (!numero.value || numero.value === previous?.numero) numero.setValue(next.numero);
    this.suggestedConsecutive.set(next);
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
      areaM2: lot.areaM2 == null ? null : Number(lot.areaM2),
      frenteM: Number(lot.frenteM ?? 0),
      fondoM: Number(lot.fondoM ?? 0),
      valorLista: lot.valorLista == null ? null : Number(lot.valorLista),
      ubicacionReferencia: lot.ubicacionReferencia ?? '',
      descripcion: lot.descripcion ?? '',
    });
    this.loadBlocks(lot.etapaId);
    this.panel.set('lote');
  }

  openCreateStage(): void {
    this.editingStage.set(null);
    this.stageForm.reset({ codigo: '', nombre: `Etapa ${this.stages().length + 1}`, fechaInicio: '', fechaFin: '', estado: 'ACTIVA', cantidadManzanas: 10 });
    this.panel.set('etapa');
  }

  /** Código que tendrá la etapa nueva si no se escribe uno (el backend lo confirma). */
  nextStageCode(): string {
    const max = Math.max(0, ...this.stages().map((stage) => stage.secuencia));
    return `E${String(max + 1).padStart(2, '0')}`;
  }

  openEditStage(stage: ProjectStage): void {
    this.editingStage.set(stage);
    this.stageForm.reset({
      codigo: stage.codigo, nombre: stage.nombre,
      fechaInicio: stage.fechaInicio ?? '', fechaFin: stage.fechaFin ?? '', estado: stage.estado, cantidadManzanas: 0,
    });
    this.panel.set('etapa');
  }

  /** "+ Manzanas": pregunta cuántas agregar a la etapa. */
  openCreateBlock(stageId?: number): void {
    const stage = this.stages().find((s) => s.id === (stageId ?? this.stageFilter()));
    if (!stage) return;
    this.quantityTarget.set({ mode: 'manzanas', stage });
    this.quantityForm.reset({ cantidad: 5 });
    this.panel.set('cantidad');
  }

  /** Pregunta cuántos lotes crear en la manzana. */
  openGenerateLots(block: Block): void {
    const stage = this.stages().find((s) => s.id === block.etapaId);
    if (!stage || !this.canGenerateLots) return;
    this.quantityTarget.set({ mode: 'lotes', stage, block });
    this.quantityForm.reset({ cantidad: 20 });
    this.panel.set('cantidad');
  }

  /**
   * Elegir una manzana muestra sus lotes; si todavía no tiene, pregunta de una vez cuántos crear.
   */
  selectBlock(block: Block): void {
    this.changeBlockFilter(`${block.id}`);
    if (!this.lotCountByBlock().get(block.id)?.total) this.openGenerateLots(block);
  }

  stageCodeOf(block: Block): string {
    return this.stages().find((stage) => stage.id === block.etapaId)?.codigo ?? '';
  }

  lotsInBlock(blockId: number): number {
    return this.lotCountByBlock().get(blockId)?.total ?? 0;
  }

  pendingInBlock(blockId: number): number {
    return this.lotCountByBlock().get(blockId)?.pendientes ?? 0;
  }

  submitQuantity(): void {
    const projectId = this.selectedProjectId();
    const target = this.quantityTarget();
    if (!projectId || !target || this.quantityForm.invalid || this.submitting()) {
      this.quantityForm.markAllAsTouched();
      return;
    }
    const cantidad = this.quantityForm.getRawValue().cantidad;
    if (target.mode === 'manzanas') {
      this.runMutation(
        this.inventory.generateBlocks(projectId, target.stage.id, cantidad),
        (blocks: Block[]) => `Se agregaron ${blocks.length} manzanas a ${target.stage.nombre} (${blocks[0]?.codigo} a ${blocks[blocks.length - 1]?.codigo}). Elige una para crear sus lotes.`,
        () => this.changeStageFilter(`${target.stage.id}`),
        this.quantityForm,
      );
      return;
    }
    this.runMutation(
      this.inventory.generateLots(projectId, target.stage.id, target.block.id, cantidad),
      (lots: Lot[]) => `Se crearon ${lots.length} lotes en ${target.block.nombre} (${lots[0]?.codigo} a ${lots[lots.length - 1]?.codigo}). Toca cada lote para ponerle medidas y precio.`,
      () => {
        this.stageFilter.set(target.stage.id);
        this.blockFilter.set(target.block.id);
        this.loadBlocks(target.stage.id);
      },
      this.quantityForm,
    );
  }

  openEditBlock(block: Block): void {
    this.editingBlock.set(block);
    this.blockForm.reset({ etapaId: block.etapaId, codigo: block.codigo, nombre: block.nombre, estado: block.estado });
    this.panel.set('manzana');
  }

  /** Abre el panel para reservar el lote elegido (datos del cliente → condiciones y pago). */
  openReserve(lot: Lot): void {
    if (lot.estado !== 'DISPONIBLE' || !this.canReserve) return;
    this.selectedLot.set(lot);
    this.panel.set('reservar');
  }

  /** Cierra el panel de reserva; si se reservó, avisa y recarga el plano (el lote ya sale reservado). */
  reserveDone(result: LotReserved | null): void {
    const lot = this.selectedLot();
    this.closePanel();
    if (result) {
      this.feedback.set(`Lote ${lot?.codigo ?? ''} reservado para ${result.cliente} (${result.reserva.codigo}). `
        + (result.pagoConfirmado ? 'Pago confirmado: reserva activa.' : 'Pago pendiente: confírmalo en Ventas.'));
    }
    this.loadProject();
  }

  closePanel(): void {
    this.panel.set(null);
    this.lotReservation.set(null);
    this.quantityTarget.set(null);
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
    if (lot.estado === 'PENDIENTE' && this.canEditLots) {
      this.openEditLot(lot);
      return;
    }
    this.selectedLot.set(lot);
    this.panel.set('detalle');
    this.loadLotReservation(lot);
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

  /** Guarda el lote; con `andNext` abre el siguiente lote por completar de la misma manzana. */
  submitLot(andNext = false): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.lotForm.invalid || this.submitting()) {
      this.lotForm.markAllAsTouched();
      return;
    }
    const value = this.lotForm.getRawValue();
    const details = {
      codigo: value.codigo.trim(),
      numero: value.numero.trim(),
      areaM2: value.areaM2 == null ? null : Number(value.areaM2),
      frenteM: value.frenteM || null,
      fondoM: value.fondoM || null,
      ubicacionReferencia: value.ubicacionReferencia.trim() || null,
      descripcion: value.descripcion.trim() || null,
    };
    const editing = this.editingLot();

    if (editing) {
      const next = andNext ? this.nextPendingLot() : null;
      const wasPending = editing.estado === 'PENDIENTE';
      this.runMutation(
        this.inventory.updateLot(projectId, editing.id, { ...details, valorLista: value.valorLista == null ? null : Number(value.valorLista) }),
        (lot: Lot) => wasPending && lot.estado === 'DISPONIBLE'
          ? `Lote ${lot.codigo} completo: ya está disponible para la venta.`
          : `Lote ${lot.codigo} actualizado.`,
        () => { if (next) this.openEditLot(next); },
        this.lotForm,
      );
      return;
    }

    this.runMutation(
      this.inventory.createLot(projectId, {
        ...details,
        areaM2: Number(value.areaM2),
        etapaId: value.etapaId,
        manzanaId: value.manzanaId,
        valorLista: Number(value.valorLista),
      }),
      'Lote creado correctamente.',
      undefined,
      this.lotForm,
    );
  }

  submitStage(): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.stageForm.invalid || this.submitting()) {
      this.stageForm.markAllAsTouched();
      return;
    }
    const value = this.stageForm.getRawValue();
    const editing = this.editingStage();
    const payload = {
      codigo: value.codigo.trim() || null,
      nombre: value.nombre.trim(),
      secuencia: editing?.secuencia ?? null,
      fechaInicio: value.fechaInicio || null,
      fechaFin: value.fechaFin || null,
      estado: value.estado,
      cantidadManzanas: editing ? null : Number(value.cantidadManzanas) || 0,
    };
    this.runMutation(
      editing ? this.inventory.updateStage(projectId, editing.id, payload) : this.inventory.createStage(projectId, payload),
      (stage: ProjectStage) => editing
        ? `Etapa ${stage.nombre} actualizada.`
        : `Etapa ${stage.nombre} (${stage.codigo}) creada${payload.cantidadManzanas ? ` con ${payload.cantidadManzanas} manzanas. Elige una manzana para crear sus lotes.` : '.'}`,
      (stage: ProjectStage) => this.changeStageFilter(`${stage.id}`),
      this.stageForm,
    );
  }

  submitBlock(): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.blockForm.invalid || this.submitting()) {
      this.blockForm.markAllAsTouched();
      return;
    }
    const { etapaId, ...value } = this.blockForm.getRawValue();
    const editing = this.editingBlock();
    const payload = { ...value, codigo: value.codigo.trim() || null, nombre: value.nombre.trim(), secuencia: editing?.secuencia ?? null };
    this.runMutation(
      editing
        ? this.inventory.updateBlock(projectId, editing.etapaId, editing.id, payload)
        : this.inventory.createBlock(projectId, etapaId, payload),
      editing ? 'Manzana actualizada correctamente.' : 'Manzana creada correctamente.',
      undefined,
      this.blockForm,
    );
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
        minPrice: this.minPrice.value || null,
        maxPrice: this.maxPrice.value || null,
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
    if (status === 'PENDIENTE') return 'Por completar';
    return status.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
  }

  stageName(id: number): string {
    return this.stages().find((stage) => stage.id === id)?.nombre ?? `Etapa #${id}`;
  }

  blockName(id: number): string {
    return (this.blocks().find((block) => block.id === id) ?? this.allBlocks().find((block) => block.id === id))?.nombre ?? `Manzana #${id}`;
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
        if (selected && this.route.snapshot.queryParamMap.get('accion') === 'nueva-etapa' && this.auth.hasAuthority('ETAPAS_GESTIONAR')) {
          this.openCreateStage();
          void this.router.navigate([], { relativeTo: this.route, queryParams: { accion: null }, queryParamsHandling: 'merge', replaceUrl: true });
        }
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
        this.loadAllBlocks(projectId, data.stages);
        this.loadAllLots(projectId);
        this.loadLots(0);
      });
  }

  /** Recorre todas las páginas de lotes del proyecto (el API entrega máximo 100 por página). */
  private loadAllLots(projectId: number): void {
    const size = 100;
    this.inventory
      .listLots({ projectId, state: '', stageId: null, blockId: null, text: '', page: 0, size })
      .pipe(
        switchMap((first) => {
          const rest = Array.from({ length: Math.max(0, first.totalPaginas - 1) }, (_, i) =>
            this.inventory.listLots({ projectId, state: '', stageId: null, blockId: null, text: '', page: i + 1, size }),
          );
          return rest.length
            ? forkJoin(rest).pipe(map((pages) => [first, ...pages].flatMap((page) => page.contenido)))
            : of(first.contenido);
        }),
        catchError(() => of([] as Lot[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((lots) => this.allLots.set(lots));
  }

  private loadLotReservation(lot: Lot): void {
    this.lotReservation.set(null);
    if (lot.estado !== 'RESERVADO' || !this.auth.hasAuthority('RESERVAS_VER')) return;
    this.sales
      .reservations({ lotId: lot.id, size: 5 })
      .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe((page) => this.lotReservation.set(
        page?.contenido.find((r) => r.estado === 'PENDIENTE_PAGO' || r.estado === 'ACTIVA') ?? null,
      ));
  }

  private loadAllBlocks(projectId: number, stages: ProjectStage[]): void {
    this.allBlocks.set([]);
    if (!stages.length || !this.auth.hasAuthority('MANZANAS_VER')) return;
    forkJoin(stages.map((stage) => this.inventory.blocks(projectId, stage.id).pipe(catchError(() => of([] as Block[])))))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((lists) => this.allBlocks.set(lists.flat()));
  }

  private loadBlocks(stageId: number): void {
    const projectId = this.selectedProjectId();
    if (!projectId || !this.auth.hasAuthority('MANZANAS_VER')) return;
    this.inventory
      .blocks(projectId, stageId)
      .pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe((blocks) => this.blocks.set(blocks));
  }

  /**
   * Ejecuta un guardado: cierra el panel, recarga el proyecto y muestra el resultado. Si la API
   * rechaza los datos, deja el panel abierto, marca los campos con su error y muestra el motivo.
   */
  private runMutation<T>(
    request: Observable<T>,
    message: string | ((result: T) => string),
    onDone?: (result: T) => void,
    form?: AbstractControl,
  ): void {
    this.submitting.set(true);
    this.feedback.set('');
    this.formError.set('');
    request
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          this.feedback.set(typeof message === 'function' ? message(result) : message);
          this.closePanel();
          this.loadProject();
          onDone?.(result);
        },
        error: (e) => {
          if (form) aplicarErroresDelServidor(form, e);
          const text = apiErrorMessage(e);
          if (this.panel()) this.formError.set(text);
          else this.feedback.set(text);
        },
      });
  }
}
