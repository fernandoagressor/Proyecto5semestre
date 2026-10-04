import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of } from 'rxjs';

import { ApiProblem } from '../../core/models/api.models';
import { CustomerLotFlowResult } from '../../core/models/customer-lot-flow.models';
import { Project } from '../../core/models/empresa.models';
import { Block, Lot, ProjectStage } from '../../core/models/inventory.models';
import { CustomerLotFlowService } from '../../core/services/customer-lot-flow.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectService } from '../../core/services/project.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';

@Component({
  selector: 'app-customer-lot-flow-page',
  imports: [ReactiveFormsModule, RouterLink, AppIconComponent, MoneyInputDirective],
  templateUrl: './customer-lot-flow.page.html',
  styleUrl: './customer-lot-flow.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerLotFlowPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly projectService = inject(ProjectService);
  private readonly inventoryService = inject(InventoryService);
  private readonly flowService = inject(CustomerLotFlowService);
  private readonly destroyRef = inject(DestroyRef);

  readonly step = signal<1 | 2 | 3 | 4>(1);
  readonly projects = signal<Project[]>([]);
  readonly stages = signal<ProjectStage[]>([]);
  readonly blocks = signal<Block[]>([]);
  readonly lots = signal<Lot[]>([]);
  readonly selectedLot = signal<Lot | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalLots = signal(0);
  readonly loadingCatalogs = signal(false);
  readonly loadingLots = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<CustomerLotFlowResult | null>(null);
  readonly minExpiry = this.localDateTime(new Date(Date.now() + 60_000));

  readonly customerForm = this.formBuilder.nonNullable.group({
    tipoPersona: this.formBuilder.nonNullable.control<'NATURAL' | 'JURIDICA'>('NATURAL'),
    tipoDocumento: ['CC', [Validators.required, Validators.maxLength(20)]],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(40)]],
    nombres: ['', [Validators.required, Validators.maxLength(120)]],
    apellidos: ['', [Validators.maxLength(120)]],
    telefono: ['', [Validators.maxLength(40)]],
    correo: ['', [Validators.email, Validators.maxLength(180)]],
    direccion: ['', [Validators.maxLength(240)]],
    origen: ['VENTAS', [Validators.maxLength(80)]],
    observaciones: ['', [Validators.maxLength(2000)]],
  });

  readonly lotFilterForm = this.formBuilder.nonNullable.group({
    projectId: [0, [Validators.required, Validators.min(1)]],
    stageId: [0],
    blockId: [0],
    text: [''],
    minArea: [null as number | null],
    maxArea: [null as number | null],
    minPrice: [null as number | null],
    maxPrice: [null as number | null],
  });

  readonly reservationForm = this.formBuilder.nonNullable.group({
    fechaVencimiento: [this.localDateTime(new Date(Date.now() + 48 * 60 * 60 * 1000)), Validators.required],
    valorReserva: [null as number | null, [Validators.required, Validators.min(0.01)]],
    formaPago: ['TRANSFERENCIA', [Validators.required, Validators.maxLength(40)]],
  });

  private readonly selectedProjectId = toSignal(this.lotFilterForm.controls.projectId.valueChanges, {
    initialValue: this.lotFilterForm.controls.projectId.value,
  });
  private readonly selectedStageId = toSignal(this.lotFilterForm.controls.stageId.valueChanges, {
    initialValue: this.lotFilterForm.controls.stageId.value,
  });
  private readonly selectedBlockId = toSignal(this.lotFilterForm.controls.blockId.valueChanges, {
    initialValue: this.lotFilterForm.controls.blockId.value,
  });
  private readonly customerFirstName = toSignal(this.customerForm.controls.nombres.valueChanges, {
    initialValue: this.customerForm.controls.nombres.value,
  });
  private readonly customerLastName = toSignal(this.customerForm.controls.apellidos.valueChanges, {
    initialValue: this.customerForm.controls.apellidos.value,
  });

  readonly selectedProject = computed(() =>
    this.projects().find((project) => project.id === Number(this.selectedProjectId())) ?? null,
  );
  readonly selectedStage = computed(() =>
    this.stages().find((stage) => stage.id === Number(this.selectedStageId())) ?? null,
  );
  readonly selectedBlock = computed(() =>
    this.blocks().find((block) => block.id === Number(this.selectedBlockId())) ?? null,
  );
  readonly customerName = computed(() =>
    `${this.customerFirstName()} ${this.customerLastName()}`.trim() || 'Nuevo cliente',
  );

  constructor() {
    this.loadProjects();
  }

  continueFromCustomer(): void {
    this.error.set(null);
    if (this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      this.error.set('Completa los datos obligatorios del cliente para continuar.');
      return;
    }
    const { telefono, correo } = this.customerForm.getRawValue();
    if (!telefono.trim() && !correo.trim()) {
      this.error.set('Ingresa al menos un teléfono o un correo para continuar con la venta.');
      return;
    }
    this.step.set(2);
  }

  changeProject(projectId: number): void {
    this.lotFilterForm.patchValue({ projectId, stageId: 0, blockId: 0 });
    this.selectedLot.set(null);
    this.stages.set([]);
    this.blocks.set([]);
    this.lots.set([]);
    if (!projectId) return;

    this.loadingCatalogs.set(true);
    forkJoin({
      stages: this.inventoryService.stages(projectId).pipe(catchError(() => of([]))),
      lots: this.inventoryService.listLots({ projectId, state: 'DISPONIBLE', page: 0, size: 25 }),
    })
      .pipe(finalize(() => this.loadingCatalogs.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ stages, lots }) => {
          this.stages.set(stages.filter((stage) => stage.estado === 'ACTIVA'));
          this.setLots(lots);
        },
        error: (error) => this.error.set(this.problemMessage(error, 'No fue posible cargar los lotes disponibles.')),
      });
  }

  changeStage(stageId: number): void {
    this.lotFilterForm.patchValue({ stageId, blockId: 0 });
    this.blocks.set([]);
    const projectId = this.lotFilterForm.controls.projectId.value;
    if (projectId && stageId) {
      this.inventoryService
        .blocks(projectId, stageId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (blocks) => this.blocks.set(blocks.filter((block) => block.estado === 'ACTIVA')),
          error: () => this.error.set('No fue posible cargar las manzanas de la etapa.'),
        });
    }
    this.loadLots(0);
  }

  changeBlock(blockId: number): void {
    this.lotFilterForm.controls.blockId.setValue(blockId);
    this.loadLots(0);
  }

  applyFilters(): void {
    const filters = this.lotFilterForm.getRawValue();
    if (filters.minArea != null && filters.maxArea != null && filters.minArea > filters.maxArea) {
      this.error.set('El área mínima no puede superar el área máxima.');
      return;
    }
    if (filters.minPrice != null && filters.maxPrice != null && filters.minPrice > filters.maxPrice) {
      this.error.set('El precio mínimo no puede superar el precio máximo.');
      return;
    }
    this.loadLots(0);
  }

  clearFilters(): void {
    const projectId = this.lotFilterForm.controls.projectId.value;
    this.lotFilterForm.reset({
      projectId,
      stageId: 0,
      blockId: 0,
      text: '',
      minArea: null,
      maxArea: null,
      minPrice: null,
      maxPrice: null,
    });
    this.blocks.set([]);
    this.loadLots(0);
  }

  selectLot(lot: Lot): void {
    this.selectedLot.set(lot);
    this.error.set(null);
  }

  continueFromLot(): void {
    if (!this.selectedLot()) {
      this.error.set('Selecciona uno de los lotes disponibles para continuar.');
      return;
    }
    this.error.set(null);
    this.step.set(3);
  }

  continueFromReservation(): void {
    this.error.set(null);
    if (this.reservationForm.invalid) {
      this.reservationForm.markAllAsTouched();
      this.error.set('Completa la fecha, el valor y la forma de pago de la reserva.');
      return;
    }
    const values = this.reservationForm.getRawValue();
    const lot = this.selectedLot();
    if (!lot) {
      this.step.set(2);
      this.error.set('Selecciona un lote disponible antes de configurar la reserva.');
      return;
    }
    if (new Date(values.fechaVencimiento).getTime() <= Date.now()) {
      this.error.set('La fecha de vencimiento debe estar en el futuro.');
      return;
    }
    if (Number(values.valorReserva) > Number(lot.valorLista)) {
      this.error.set('El valor de la reserva no puede superar el precio del lote.');
      return;
    }
    this.step.set(4);
  }

  confirm(): void {
    if (this.submitting()) return;

    this.error.set(null);
    const lot = this.selectedLot();
    const project = this.selectedProject();
    if (!lot) {
      this.step.set(2);
      this.error.set('Selecciona un lote disponible antes de confirmar la reserva.');
      return;
    }
    if (!project) {
      this.step.set(2);
      this.error.set('Selecciona un proyecto válido antes de confirmar la reserva.');
      return;
    }
    if (lot.proyectoId !== project.id) {
      this.step.set(2);
      this.error.set('El lote seleccionado no pertenece al proyecto actual. Selecciónalo nuevamente.');
      return;
    }
    if (this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      this.step.set(1);
      this.error.set('Completa los datos obligatorios del cliente antes de confirmar.');
      return;
    }
    if (this.reservationForm.invalid) {
      this.reservationForm.markAllAsTouched();
      this.step.set(3);
      this.error.set('Completa los datos de la reserva antes de confirmar.');
      return;
    }

    this.submitting.set(true);
    const customer = this.customerForm.getRawValue();
    const reservation = this.reservationForm.getRawValue();
    this.flowService
      .create({
        cliente: {
          tipoPersona: customer.tipoPersona,
          tipoDocumento: customer.tipoDocumento.trim(),
          numeroDocumento: customer.numeroDocumento.trim(),
          nombres: customer.nombres.trim(),
          apellidos: this.nullIfBlank(customer.apellidos),
          telefono: this.nullIfBlank(customer.telefono),
          correo: this.nullIfBlank(customer.correo),
          direccion: this.nullIfBlank(customer.direccion),
          origen: this.nullIfBlank(customer.origen),
          observaciones: this.nullIfBlank(customer.observaciones),
        },
        proyectoId: project.id,
        loteId: lot.id,
        fechaVencimiento: reservation.fechaVencimiento,
        valorReserva: Number(reservation.valorReserva),
        formaPago: reservation.formaPago,
      })
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.success.set(result),
        error: (error) => this.error.set(this.problemMessage(
          error,
          'No fue posible crear la reserva. Ningún cambio parcial fue guardado.',
        )),
      });
  }

  goBack(): void {
    if (this.step() > 1) this.step.set((this.step() - 1) as 1 | 2 | 3 | 4);
    this.error.set(null);
  }

  loadLots(page: number): void {
    const filters = this.lotFilterForm.getRawValue();
    if (!filters.projectId) return;
    this.error.set(null);
    this.loadingLots.set(true);
    this.inventoryService
      .listLots({
        projectId: filters.projectId,
        state: 'DISPONIBLE',
        stageId: filters.stageId || null,
        blockId: filters.blockId || null,
        text: filters.text.trim(),
        minArea: filters.minArea,
        maxArea: filters.maxArea,
        minPrice: filters.minPrice,
        maxPrice: filters.maxPrice,
        page,
        size: 25,
      })
      .pipe(finalize(() => this.loadingLots.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.setLots(result),
        error: (error) => this.error.set(this.problemMessage(error, 'No fue posible consultar los lotes.')),
      });
  }

  money(value: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
    }).format(Number(value ?? 0));
  }

  private loadProjects(): void {
    this.loadingCatalogs.set(true);
    this.projectService
      .list({ status: 'ACTIVO', size: 100 })
      .pipe(finalize(() => this.loadingCatalogs.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => this.projects.set(result.contenido),
        error: (error) => this.error.set(this.problemMessage(error, 'No fue posible cargar los proyectos activos.')),
      });
  }

  private setLots(result: { contenido: Lot[]; pagina: number; totalPaginas: number; totalElementos: number }): void {
    this.lots.set(result.contenido);
    this.currentPage.set(result.pagina);
    this.totalPages.set(result.totalPaginas);
    this.totalLots.set(result.totalElementos);
    const selected = this.selectedLot();
    if (selected && !result.contenido.some((lot) => lot.id === selected.id)) this.selectedLot.set(null);
  }

  private nullIfBlank(value: string): string | null {
    const normalized = value.trim();
    return normalized ? normalized : null;
  }

  private localDateTime(date: Date): string {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
    return local.toISOString().slice(0, 16);
  }

  private problemMessage(error: unknown, fallback: string): string {
    if (!(error instanceof HttpErrorResponse)) return fallback;
    const problem = error.error as ApiProblem | undefined;
    const fieldMessage = problem?.errores ? Object.values(problem.errores)[0] : undefined;
    return fieldMessage ?? problem?.detail ?? fallback;
  }
}
