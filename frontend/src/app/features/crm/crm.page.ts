import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  forkJoin,
  Observable,
  of,
} from 'rxjs';

import { PageResponse } from '../../core/models/api.models';
import {
  Appointment,
  Opportunity,
  OpportunityLot,
  OpportunityStage,
  Periodicity,
  Prospect,
  ProspectSource,
  ProspectTemperature,
  Simulation,
} from '../../core/models/crm.models';
import { Project } from '../../core/models/empresa.models';
import { Lot } from '../../core/models/inventory.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectService } from '../../core/services/project.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';

type CrmTab = 'pipeline' | 'prospectos' | 'agenda' | 'simulaciones';
type CrmPanel = 'prospecto' | 'oportunidad' | 'lotes' | 'cita' | 'simulacion' | null;

interface StageDefinition {
  value: OpportunityStage;
  label: string;
  probability: number;
}

@Component({
  selector: 'app-crm-page',
  imports: [ReactiveFormsModule, AppIconComponent, MoneyInputDirective],
  templateUrl: './crm.page.html',
  styleUrl: './crm.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CrmPage {
  private readonly crm = inject(CrmService);
  private readonly projectsService = inject(ProjectService);
  private readonly inventory = inject(InventoryService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly projects = signal<Project[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  readonly prospects = signal<PageResponse<Prospect> | null>(null);
  readonly opportunities = signal<PageResponse<Opportunity> | null>(null);
  readonly appointments = signal<PageResponse<Appointment> | null>(null);
  readonly simulations = signal<PageResponse<Simulation> | null>(null);
  readonly availableLots = signal<Lot[]>([]);
  readonly selectedOpportunity = signal<Opportunity | null>(null);
  readonly opportunityLots = signal<OpportunityLot[]>([]);
  readonly opportunityProjectLots = signal<Lot[]>([]);
  readonly lotManagementLoading = signal(false);
  readonly lotError = signal('');
  readonly tab = signal<CrmTab>('pipeline');
  readonly panel = signal<CrmPanel>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal(false);
  readonly feedback = signal('');
  readonly search = new FormControl('', { nonNullable: true });

  readonly stages: readonly StageDefinition[] = [
    { value: 'PROSPECTO_RECIBIDO', label: 'Recibido', probability: 5 },
    { value: 'CONTACTO_INICIAL', label: 'Contacto', probability: 10 },
    { value: 'CALIFICACION', label: 'Calificación', probability: 25 },
    { value: 'PRESENTACION_PROYECTO', label: 'Presentación', probability: 40 },
    { value: 'VISITA', label: 'Visita', probability: 55 },
    { value: 'PROPUESTA', label: 'Propuesta', probability: 70 },
    { value: 'NEGOCIACION', label: 'Negociación', probability: 85 },
    { value: 'CIERRE', label: 'Cierre', probability: 95 },
  ];
  readonly sources: readonly { value: ProspectSource; label: string }[] = [
    { value: 'WEB', label: 'Sitio web' },
    { value: 'REDES_SOCIALES', label: 'Redes sociales' },
    { value: 'REFERIDO', label: 'Referido' },
    { value: 'PUBLICIDAD', label: 'Publicidad' },
    { value: 'FERIA', label: 'Feria' },
    { value: 'LLAMADA', label: 'Llamada' },
    { value: 'VISITA', label: 'Visita' },
    { value: 'OTRO', label: 'Otro' },
  ];
  readonly temperatures: readonly ProspectTemperature[] = ['FRIO', 'TIBIO', 'CALIENTE'];
  readonly periodicities: readonly Periodicity[] = [
    'MENSUAL',
    'BIMESTRAL',
    'TRIMESTRAL',
    'SEMESTRAL',
    'ANUAL',
  ];

  readonly prospectForm = this.fb.nonNullable.group({
    nombres: ['', [Validators.required, Validators.maxLength(160)]],
    telefono: ['', Validators.maxLength(40)],
    correo: ['', [Validators.email, Validators.maxLength(180)]],
    fuente: ['WEB' as ProspectSource, Validators.required],
    presupuesto: [0, Validators.min(0)],
    temperatura: ['TIBIO' as ProspectTemperature, Validators.required],
    observaciones: ['', Validators.maxLength(5000)],
  });
  readonly opportunityForm = this.fb.nonNullable.group({
    prospectoId: [0, [Validators.required, Validators.min(1)]],
    valorEstimado: [0, Validators.min(0)],
    fechaProximaAccion: [''],
  });
  readonly opportunityLotForm = this.fb.nonNullable.group({
    loteId: [0, [Validators.required, Validators.min(1)]],
    prioridad: [1, [Validators.required, Validators.min(1), Validators.max(10)]],
  });
  readonly appointmentForm = this.fb.nonNullable.group({
    oportunidadId: [0, [Validators.required, Validators.min(1)]],
    tipo: ['VISITA', [Validators.required, Validators.maxLength(30)]],
    fechaInicio: ['', Validators.required],
    fechaFin: ['', Validators.required],
    lugar: ['', Validators.maxLength(300)],
  });
  readonly simulationForm = this.fb.nonNullable.group({
    oportunidadId: [0, [Validators.required, Validators.min(1)]],
    loteId: [0, [Validators.required, Validators.min(1)]],
    descuento: [0, Validators.min(0)],
    cuotaInicial: [0, Validators.min(0)],
    numeroCuotas: [12, [Validators.required, Validators.min(1), Validators.max(600)]],
    tasaEfectivaAnual: [0, [Validators.required, Validators.min(0), Validators.max(9.999999)]],
    periodicidad: ['MENSUAL' as Periodicity, Validators.required],
    fechaPrimeraCuota: ['', Validators.required],
    vigenteHasta: ['', Validators.required],
  });

  constructor() {
    if (!this.auth.hasAuthority('OPORTUNIDADES_VER') && this.auth.hasAuthority('PROSPECTOS_VER')) {
      this.tab.set('prospectos');
    }
    this.search.valueChanges
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.loadOperationalData());
    this.loadProjects();
  }

  selectProject(rawId: string): void {
    this.selectedProjectId.set(Number(rawId) || null);
    this.loadOperationalData();
  }

  selectTab(tab: CrmTab): void {
    this.tab.set(tab);
  }

  reload(): void {
    this.loadOperationalData();
  }

  openProspect(): void {
    this.prospectForm.reset({
      nombres: '',
      telefono: '',
      correo: '',
      fuente: 'WEB',
      presupuesto: 0,
      temperatura: 'TIBIO',
      observaciones: '',
    });
    this.panel.set('prospecto');
  }

  openOpportunity(prospect?: Prospect): void {
    this.opportunityForm.reset({
      prospectoId: prospect?.id ?? 0,
      valorEstimado: Number(prospect?.presupuesto ?? 0),
      fechaProximaAccion: '',
    });
    this.panel.set('oportunidad');
  }

  openAppointment(opportunity?: Opportunity): void {
    const start = this.nextWorkingHour();
    const end = new Date(start.getTime() + 60 * 60 * 1000);
    this.appointmentForm.reset({
      oportunidadId: opportunity?.id ?? 0,
      tipo: 'VISITA',
      fechaInicio: this.toInputDateTime(start),
      fechaFin: this.toInputDateTime(end),
      lugar: '',
    });
    this.panel.set('cita');
  }

  openLotManager(opportunity: Opportunity): void {
    this.selectedOpportunity.set(opportunity);
    this.opportunityLots.set([]);
    this.opportunityProjectLots.set([]);
    this.feedback.set('');
    this.lotError.set('');
    this.opportunityLotForm.reset({ loteId: 0, prioridad: 1 });
    this.panel.set('lotes');
    this.loadOpportunityLots();
  }

  openSimulation(opportunity?: Opportunity): void {
    const firstPayment = new Date();
    firstPayment.setMonth(firstPayment.getMonth() + 1);
    const validUntil = new Date();
    validUntil.setDate(validUntil.getDate() + 7);
    this.simulationForm.reset({
      oportunidadId: opportunity?.id ?? 0,
      loteId: 0,
      descuento: 0,
      cuotaInicial: 0,
      numeroCuotas: 12,
      tasaEfectivaAnual: 0,
      periodicidad: 'MENSUAL',
      fechaPrimeraCuota: this.toDate(firstPayment),
      vigenteHasta: this.toInputDateTime(validUntil),
    });
    this.loadAvailableLots();
    this.panel.set('simulacion');
  }

  closePanel(): void {
    this.panel.set(null);
  }

  submitProspect(): void {
    if (this.prospectForm.invalid || this.submitting()) {
      this.prospectForm.markAllAsTouched();
      return;
    }
    const value = this.prospectForm.getRawValue();
    this.runMutation(
      this.crm.createProspect({
        proyectoId: this.selectedProjectId(),
        asesorId: this.auth.user()?.id ?? null,
        nombres: value.nombres.trim(),
        telefono: value.telefono.trim() || null,
        correo: value.correo.trim() || null,
        fuente: value.fuente,
        presupuesto: value.presupuesto || null,
        temperatura: value.temperatura,
        observaciones: value.observaciones.trim() || null,
      }),
      'Prospecto registrado correctamente.',
    );
  }

  submitOpportunity(): void {
    const projectId = this.selectedProjectId();
    if (!projectId || this.opportunityForm.invalid || this.submitting()) {
      this.opportunityForm.markAllAsTouched();
      return;
    }
    const value = this.opportunityForm.getRawValue();
    this.runMutation(
      this.crm.createOpportunity({
        proyectoId: projectId,
        prospectoId: value.prospectoId,
        asesorId: this.auth.user()?.id ?? null,
        valorEstimado: value.valorEstimado || null,
        fechaProximaAccion: value.fechaProximaAccion
          ? this.normalizeDateTime(value.fechaProximaAccion)
          : null,
      }),
      'Oportunidad creada en el embudo.',
    );
  }

  submitAppointment(): void {
    if (this.appointmentForm.invalid || this.submitting()) {
      this.appointmentForm.markAllAsTouched();
      return;
    }
    const value = this.appointmentForm.getRawValue();
    if (new Date(value.fechaFin) <= new Date(value.fechaInicio)) {
      this.feedback.set('La hora de finalización debe ser posterior al inicio.');
      return;
    }
    this.runMutation(
      this.crm.createAppointment(value.oportunidadId, {
        tipo: value.tipo,
        fechaInicio: this.normalizeDateTime(value.fechaInicio),
        fechaFin: this.normalizeDateTime(value.fechaFin),
        lugar: value.lugar.trim() || null,
      }),
      'Cita añadida a la agenda comercial.',
    );
  }

  submitSimulation(): void {
    if (this.simulationForm.invalid || this.submitting()) {
      this.simulationForm.markAllAsTouched();
      return;
    }
    const value = this.simulationForm.getRawValue();
    this.runMutation(
      this.crm.createSimulation({
        ...value,
        vigenteHasta: this.normalizeDateTime(value.vigenteHasta),
      }),
      'Simulación financiera creada.',
    );
  }

  submitOpportunityLot(): void {
    const opportunity = this.selectedOpportunity();
    if (!opportunity || this.opportunityLotForm.invalid || this.submitting()) {
      this.opportunityLotForm.markAllAsTouched();
      return;
    }
    const value = this.opportunityLotForm.getRawValue();
    this.runLotMutation(
      this.crm.addOpportunityLot(opportunity.id, value.loteId, value.prioridad),
      'Lote vinculado a la oportunidad.',
      () => this.loadOpportunityLots(),
    );
  }

  selectOpportunityLot(relation: OpportunityLot): void {
    const opportunity = this.selectedOpportunity();
    if (!opportunity || relation.seleccionado || this.submitting()) return;
    this.runLotMutation(
      this.crm.selectOpportunityLot(opportunity.id, relation.loteId),
      'Lote marcado como preferido para la oportunidad.',
      () =>
        this.opportunityLots.update((items) =>
          items.map((item) => ({
            ...item,
            seleccionado: item.loteId === relation.loteId,
          })),
        ),
    );
  }

  removeOpportunityLot(relation: OpportunityLot): void {
    const opportunity = this.selectedOpportunity();
    if (!opportunity || this.submitting()) return;
    const lot = this.lotFor(relation.loteId);
    if (
      !window.confirm(
        `¿Quitar el lote ${lot?.codigo ?? '#' + relation.loteId} de esta oportunidad?`,
      )
    )
      return;
    this.runLotMutation(
      this.crm.removeOpportunityLot(opportunity.id, relation.loteId),
      'Lote retirado de la oportunidad.',
      () => {
        this.opportunityLots.update((items) =>
          items.filter((item) => item.loteId !== relation.loteId),
        );
        const next = this.availableOpportunityLots()[0];
        this.opportunityLotForm.patchValue({ loteId: next?.id ?? 0 });
      },
    );
  }

  qualify(prospect: Prospect): void {
    if (this.submitting()) return;
    this.runMutation(
      this.crm.qualifyProspect(
        prospect.id,
        'CALIFICADO',
        prospect.temperatura,
        'Calificado desde la bandeja comercial',
      ),
      'Prospecto calificado.',
    );
  }

  advance(opportunity: Opportunity): void {
    const currentIndex = this.stages.findIndex((stage) => stage.value === opportunity.etapaActual);
    const next = this.stages[currentIndex + 1];
    if (!next || this.submitting()) return;
    this.runMutation(
      this.crm.moveOpportunity(
        opportunity.id,
        next.value,
        next.probability,
        'Avance desde el tablero comercial',
      ),
      `Oportunidad movida a ${next.label}.`,
    );
  }

  sendSimulation(simulation: Simulation): void {
    if (this.submitting()) return;
    this.runMutation(this.crm.sendSimulation(simulation.id), 'Simulación enviada y registrada.');
  }

  opportunitiesAt(stage: OpportunityStage): Opportunity[] {
    return (this.opportunities()?.contenido ?? []).filter(
      (item) => item.etapaActual === stage && item.estado === 'ABIERTA',
    );
  }

  prospectName(id: number): string {
    return (
      this.prospects()?.contenido.find((prospect) => prospect.id === id)?.nombres ??
      `Prospecto #${id}`
    );
  }

  prospectNameForOpportunity(opportunityId: number): string {
    const opportunity = this.opportunities()?.contenido.find((item) => item.id === opportunityId);
    return this.prospectName(opportunity?.prospectoId ?? 0);
  }

  projectName(id: number): string {
    if (!id) return 'Sin proyecto asignado';
    return this.projects().find((project) => project.id === id)?.nombre ?? `Proyecto #${id}`;
  }

  openOpportunityCount(): number {
    return (this.opportunities()?.contenido ?? []).filter((item) => item.estado === 'ABIERTA')
      .length;
  }

  currency(value: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
    }).format(Number(value ?? 0));
  }

  label(value: string): string {
    return value
      .replaceAll('_', ' ')
      .toLowerCase()
      .replace(/^./, (letter) => letter.toUpperCase());
  }

  dateTime(value: string | null): string {
    if (!value) return 'Sin programar';
    return new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(
      new Date(value),
    );
  }

  pipelineValue(): number {
    return (this.opportunities()?.contenido ?? [])
      .filter((item) => item.estado === 'ABIERTA')
      .reduce((total, item) => total + Number(item.valorEstimado ?? 0), 0);
  }

  availableOpportunityLots(): Lot[] {
    const linkedIds = new Set(this.opportunityLots().map((item) => item.loteId));
    return this.opportunityProjectLots().filter(
      (lot) => lot.estado === 'DISPONIBLE' && !linkedIds.has(lot.id),
    );
  }

  lotFor(lotId: number): Lot | undefined {
    return this.opportunityProjectLots().find((lot) => lot.id === lotId);
  }

  private loadProjects(): void {
    this.projectsService
      .list({ size: 100 })
      .pipe(
        catchError(() => {
          this.error.set(true);
          return of(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        if (!page) return;
        this.projects.set(page.contenido);
        this.selectedProjectId.set(page.contenido[0]?.id ?? null);
        this.loadOperationalData();
      });
  }

  private loadOperationalData(): void {
    this.loading.set(true);
    this.error.set(false);
    const projectId = this.selectedProjectId();
    const text = this.search.value.trim();
    const now = new Date();
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    forkJoin({
      prospects: this.auth.hasAuthority('PROSPECTOS_VER')
        ? this.crm.prospects({ text, projectId, size: 100 }).pipe(catchError(() => of(null)))
        : of(null),
      opportunities: this.auth.hasAuthority('OPORTUNIDADES_VER')
        ? this.crm.opportunities({ text, projectId, size: 100 }).pipe(catchError(() => of(null)))
        : of(null),
      appointments: this.auth.hasAuthority('AGENDA_VER')
        ? this.crm
            .appointments({
              from: this.toApiDateTime(monthStart),
              to: this.toApiDateTime(monthEnd),
              size: 100,
            })
            .pipe(catchError(() => of(null)))
        : of(null),
      simulations: this.auth.hasAuthority('SIMULACIONES_VER')
        ? this.crm.simulations({ projectId, size: 50 }).pipe(catchError(() => of(null)))
        : of(null),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((data) => {
        this.prospects.set(data.prospects);
        this.opportunities.set(data.opportunities);
        this.appointments.set(data.appointments);
        this.simulations.set(data.simulations);
      });
  }

  private loadAvailableLots(): void {
    const projectId = this.selectedProjectId();
    if (!projectId) return;
    this.inventory
      .listLots({ projectId, state: 'DISPONIBLE', size: 100 })
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => this.availableLots.set(page?.contenido ?? []));
  }

  private loadOpportunityLots(): void {
    const opportunity = this.selectedOpportunity();
    if (!opportunity) return;
    this.lotManagementLoading.set(true);
    this.lotError.set('');
    forkJoin({
      relations: this.crm.opportunityLots(opportunity.id),
      lots: this.inventory.listLots({ projectId: opportunity.proyectoId, size: 100 }),
    })
      .pipe(
        finalize(() => this.lotManagementLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ relations, lots }) => {
          this.opportunityLots.set(relations);
          this.opportunityProjectLots.set(lots.contenido);
          const first = lots.contenido.find(
            (lot) =>
              lot.estado === 'DISPONIBLE' &&
              !relations.some((relation) => relation.loteId === lot.id),
          );
          this.opportunityLotForm.reset({
            loteId: first?.id ?? 0,
            prioridad: Math.min(relations.length + 1, 10),
          });
        },
        error: (error) =>
          this.lotError.set(
            this.errorMessage(error, 'No fue posible cargar los lotes de la oportunidad.'),
          ),
      });
  }

  private runLotMutation(request: Observable<unknown>, message: string, done: () => void): void {
    this.submitting.set(true);
    this.lotError.set('');
    this.feedback.set('');
    request
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.feedback.set(message);
          done();
        },
        error: (error) =>
          this.lotError.set(
            this.errorMessage(error, 'No fue posible completar la operación con el lote.'),
          ),
      });
  }

  private errorMessage(error: unknown, fallback: string): string {
    const response = error as { error?: { detail?: string; title?: string }; message?: string };
    return response?.error?.detail ?? response?.error?.title ?? response?.message ?? fallback;
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
          this.loadOperationalData();
        },
        error: () =>
          this.feedback.set(
            'No fue posible completar la operación. Revisa la información e inténtalo nuevamente.',
          ),
      });
  }

  private nextWorkingHour(): Date {
    const date = new Date();
    date.setHours(date.getHours() + 1, 0, 0, 0);
    return date;
  }

  private toInputDateTime(date: Date): string {
    return `${this.toDate(date)}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }

  private toDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private toApiDateTime(date: Date): string {
    return `${this.toInputDateTime(date)}:00`;
  }

  private normalizeDateTime(value: string): string {
    return value.length === 16 ? `${value}:00` : value;
  }
}
