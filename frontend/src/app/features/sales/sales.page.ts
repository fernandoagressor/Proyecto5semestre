import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, finalize, forkJoin, map, of, switchMap } from 'rxjs';

import { Opportunity, Simulation } from '../../core/models/crm.models';
import { Project } from '../../core/models/empresa.models';
import { Lot } from '../../core/models/inventory.models';
import {
  Contract,
  ContractConditionsPayload,
  ContractSigner,
  Customer,
  CustomerPayload,
  Reservation,
} from '../../core/models/sales.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { InventoryService } from '../../core/services/inventory.service';
import { ProjectService } from '../../core/services/project.service';
import { SalesService } from '../../core/services/sales.service';
import { NX_UI } from '../../shared/ui';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';

type SalesTab = 'reservas' | 'contratos' | 'clientes';
type SalesModal =
  | 'cliente'
  | 'reserva'
  | 'pago'
  | 'extender'
  | 'motivo-reserva'
  | 'convertir'
  | 'documento-contrato'
  | 'participante'
  | 'firmante'
  | 'firma'
  | 'anular-contrato'
  | null;

@Component({
  selector: 'app-sales-page',
  imports: [CommonModule, ReactiveFormsModule, RouterLink, ...NX_UI, MoneyInputDirective],
  templateUrl: './sales.page.html',
  styleUrl: './sales.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalesPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly sales = inject(SalesService);
  private readonly projectsService = inject(ProjectService);
  private readonly crm = inject(CrmService);
  private readonly inventory = inject(InventoryService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeTab = signal<SalesTab>('reservas');
  readonly projects = signal<Project[]>([]);
  readonly reservations = signal<Reservation[]>([]);
  readonly contracts = signal<Contract[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly opportunities = signal<Opportunity[]>([]);
  readonly opportunityLots = signal<Lot[]>([]);
  readonly simulations = signal<Simulation[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  readonly reservationStatus = signal('');
  readonly contractStatus = signal('');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly feedback = signal('');
  readonly error = signal('');
  readonly modal = signal<SalesModal>(null);
  readonly selectedReservation = signal<Reservation | null>(null);
  readonly selectedContract = signal<Contract | null>(null);
  readonly editingCustomer = signal<Customer | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly reservationReasonAction = signal<'cancelar' | 'rechazar'>('cancelar');

  readonly customerSearch = this.fb.nonNullable.control('');

  readonly customerForm = this.fb.nonNullable.group({
    tipoPersona: ['NATURAL' as 'NATURAL' | 'JURIDICA', Validators.required],
    tipoDocumento: ['CC', [Validators.required, Validators.maxLength(20)]],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(40)]],
    nombres: ['', [Validators.required, Validators.maxLength(120)]],
    apellidos: ['', Validators.maxLength(120)],
    telefono: ['', Validators.maxLength(40)],
    correo: ['', [Validators.email, Validators.maxLength(180)]],
    direccion: ['', Validators.maxLength(240)],
    origen: ['VENTAS', Validators.maxLength(80)],
    observaciones: ['', Validators.maxLength(2000)],
  });

  readonly reservationForm = this.fb.nonNullable.group({
    projectId: [0, [Validators.required, Validators.min(1)]],
    opportunityId: [0, [Validators.required, Validators.min(1)]],
    customerId: [0, [Validators.required, Validators.min(1)]],
    lotId: [0, [Validators.required, Validators.min(1)]],
    simulationId: [0],
    expiration: ['', Validators.required],
    value: [0, [Validators.required, Validators.min(0.01)]],
    paymentMethod: ['TRANSFERENCIA', [Validators.required, Validators.maxLength(40)]],
  });

  readonly dateForm = this.fb.nonNullable.group({
    expiration: ['', Validators.required],
  });

  readonly reasonForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(400)]],
  });

  readonly contractForm = this.fb.nonNullable.group({
    type: ['PROMESA_COMPRAVENTA' as ContractConditionsPayload['tipoContrato'], Validators.required],
    otherConcepts: [0, [Validators.required, Validators.min(0)]],
    discount: [0, [Validators.required, Validators.min(0)]],
    downPayment: [0, [Validators.required, Validators.min(0)]],
    installments: [1, [Validators.required, Validators.min(1)]],
    startDate: [this.localDate(new Date()), Validators.required],
    endDate: [''],
  });

  readonly documentForm = this.fb.nonNullable.group({
    reason: ['Versión contractual para firma', [Validators.required, Validators.maxLength(400)]],
  });

  readonly participantForm = this.fb.nonNullable.group({
    customerId: [0, [Validators.required, Validators.min(1)]],
    role: ['COPROPIETARIO' as 'COPROPIETARIO' | 'BENEFICIARIO' | 'APODERADO', Validators.required],
    participation: [10, [Validators.required, Validators.min(0), Validators.max(100)]],
  });

  readonly signerForm = this.fb.nonNullable.group({
    customerId: [0, [Validators.required, Validators.min(1)]],
    type: [
      'COPROPIETARIO' as 'COMPRADOR' | 'COPROPIETARIO' | 'REPRESENTANTE' | 'VENDEDOR' | 'TESTIGO',
      Validators.required,
    ],
    order: [2, [Validators.required, Validators.min(1)]],
    scheduledAt: [''],
  });

  readonly signatureForm = this.fb.nonNullable.group({
    signerId: [0, [Validators.required, Validators.min(1)]],
    method: ['ELECTRONICA' as 'MANUSCRITA' | 'ELECTRONICA' | 'DIGITAL', Validators.required],
    provider: ['Nexora Core'],
    externalReference: [''],
    signedAt: [''],
  });

  readonly activeCustomers = computed(() =>
    this.customers().filter((customer) => customer.estado === 'ACTIVO'),
  );
  readonly pendingReservations = computed(
    () =>
      this.reservations().filter((reservation) => reservation.estado === 'PENDIENTE_PAGO').length,
  );
  readonly activeReservations = computed(
    () => this.reservations().filter((reservation) => reservation.estado === 'ACTIVA').length,
  );
  readonly currentContracts = computed(
    () => this.contracts().filter((contract) => contract.estado === 'VIGENTE').length,
  );
  readonly salesValue = computed(() =>
    this.contracts()
      .filter((contract) => contract.estado === 'VIGENTE')
      .reduce((total, contract) => total + Number(contract.valorTotal), 0),
  );

  ngOnInit(): void {
    if (!this.auth.hasAuthority('RESERVAS_VER') && this.auth.hasAuthority('CONTRATOS_VER')) {
      this.activeTab.set('contratos');
    }
    this.customerSearch.valueChanges
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadCustomers());
    this.loadInitialData();
  }

  setTab(tab: SalesTab): void {
    this.activeTab.set(tab);
    this.clearMessages();
  }

  changeProject(value: string): void {
    const projectId = Number(value) || null;
    this.selectedProjectId.set(projectId);
    this.loadReservations();
    this.loadContracts();
  }

  filterReservations(value: string): void {
    this.reservationStatus.set(value);
    this.loadReservations();
  }

  filterContracts(value: string): void {
    this.contractStatus.set(value);
    this.loadContracts();
  }

  openCustomer(customer?: Customer): void {
    this.editingCustomer.set(customer ?? null);
    if (customer) {
      this.customerForm.reset({
        tipoPersona: customer.tipoPersona,
        tipoDocumento: customer.tipoDocumento,
        numeroDocumento: customer.numeroDocumento,
        nombres: customer.nombres,
        apellidos: customer.apellidos ?? '',
        telefono: customer.telefono ?? '',
        correo: customer.correo ?? '',
        direccion: customer.direccion ?? '',
        origen: customer.origen ?? '',
        observaciones: customer.observaciones ?? '',
      });
    } else {
      this.customerForm.reset({
        tipoPersona: 'NATURAL',
        tipoDocumento: 'CC',
        numeroDocumento: '',
        nombres: '',
        apellidos: '',
        telefono: '',
        correo: '',
        direccion: '',
        origen: 'VENTAS',
        observaciones: '',
      });
    }
    this.modal.set('cliente');
  }

  saveCustomer(): void {
    if (this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      return;
    }
    const value = this.customerForm.getRawValue();
    const payload: CustomerPayload = {
      tipoPersona: value.tipoPersona,
      tipoDocumento: value.tipoDocumento.trim(),
      numeroDocumento: value.numeroDocumento.trim(),
      nombres: value.nombres.trim(),
      apellidos: this.nullIfBlank(value.apellidos),
      telefono: this.nullIfBlank(value.telefono),
      correo: this.nullIfBlank(value.correo),
      direccion: this.nullIfBlank(value.direccion),
      origen: this.nullIfBlank(value.origen),
      observaciones: this.nullIfBlank(value.observaciones),
    };
    const editing = this.editingCustomer();
    const request = editing
      ? this.sales.updateCustomer(editing.id, payload)
      : this.sales.createCustomer(payload);
    this.runSave(request, editing ? 'Cliente actualizado' : 'Cliente creado', () => {
      this.closeModal();
      this.loadCustomers();
    });
  }

  toggleCustomer(customer: Customer): void {
    const next = customer.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    this.runSave(
      this.sales.changeCustomerStatus(customer.id, next),
      `Cliente ${next.toLowerCase()}`,
      () => this.loadCustomers(),
    );
  }

  openReservation(): void {
    const projectId = this.selectedProjectId() ?? this.projects()[0]?.id ?? 0;
    this.reservationForm.reset({
      projectId,
      opportunityId: 0,
      customerId: this.activeCustomers()[0]?.id ?? 0,
      lotId: 0,
      simulationId: 0,
      expiration: this.localDateTime(new Date(Date.now() + 72 * 60 * 60 * 1000)),
      value: 0,
      paymentMethod: 'TRANSFERENCIA',
    });
    this.opportunityLots.set([]);
    this.simulations.set([]);
    this.loadOpportunities(projectId);
    this.modal.set('reserva');
  }

  reservationProjectChanged(value: string): void {
    const projectId = Number(value);
    this.reservationForm.patchValue({ projectId, opportunityId: 0, lotId: 0, simulationId: 0 });
    this.opportunityLots.set([]);
    this.simulations.set([]);
    if (projectId) this.loadOpportunities(projectId);
  }

  opportunityChanged(value: string): void {
    const opportunityId = Number(value);
    this.reservationForm.patchValue({ opportunityId, lotId: 0, simulationId: 0 });
    this.opportunityLots.set([]);
    this.simulations.set([]);
    if (!opportunityId) return;
    const projectId = this.reservationForm.controls.projectId.value;
    forkJoin({
      relations: this.crm.opportunityLots(opportunityId),
      simulations: this.crm.simulations({ opportunityId, projectId, size: 50 }),
    })
      .pipe(
        switchMap(({ relations, simulations }) => {
          this.simulations.set(simulations.contenido.filter((item) => item.estado === 'ENVIADA'));
          if (!relations.length)
            return of([] as { lot: Lot; selected: boolean; priority: number }[]);
          return forkJoin(
            relations.map((relation) =>
              this.inventory.lot(projectId, relation.loteId).pipe(
                map((lot) => ({
                  lot,
                  selected: relation.seleccionado,
                  priority: relation.prioridad,
                })),
              ),
            ),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (relations) => {
          const lots = relations
            .filter((item) => item.lot.estado === 'DISPONIBLE')
            .sort(
              (left, right) =>
                Number(right.selected) - Number(left.selected) || left.priority - right.priority,
            )
            .map((item) => item.lot);
          this.opportunityLots.set(lots);
          const first = lots[0];
          if (first)
            this.reservationForm.patchValue({
              lotId: first.id,
              value: Math.min(Number(first.valorLista), 500000),
            });
        },
        error: (error) => this.showError(error),
      });
  }

  saveReservation(): void {
    if (this.reservationForm.invalid) {
      this.reservationForm.markAllAsTouched();
      return;
    }
    const value = this.reservationForm.getRawValue();
    this.runSave(
      this.sales.createReservation({
        oportunidadId: value.opportunityId,
        simulacionId: value.simulationId || null,
        clienteId: value.customerId,
        loteId: value.lotId,
        fechaVencimiento: value.expiration,
        valorReserva: value.value,
        formaPago: value.paymentMethod.trim(),
      }),
      'Reserva creada y lote bloqueado',
      () => {
        this.closeModal();
        this.loadReservations();
      },
    );
  }

  viewReservation(reservation: Reservation): void {
    this.selectedContract.set(null);
    this.loading.set(true);
    this.sales
      .reservation(reservation.id)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (item) => this.selectedReservation.set(item),
        error: (error) => this.showError(error),
      });
  }

  openPayment(reservation: Reservation): void {
    this.selectedReservation.set(reservation);
    this.selectedFile.set(null);
    this.modal.set('pago');
  }

  confirmPayment(): void {
    const reservation = this.selectedReservation();
    const file = this.selectedFile();
    if (!reservation || !file) {
      this.error.set('Selecciona el comprobante de pago.');
      return;
    }
    this.runSave(
      this.sales
        .uploadDocument(reservation.proyectoId, file)
        .pipe(
          switchMap((document) =>
            this.sales.confirmReservationPayment(reservation.id, document.id),
          ),
        ),
      'Pago confirmado y comprobante almacenado',
      (updatedReservation) => {
        this.closeModal();
        this.selectedReservation.set(updatedReservation);
        this.replaceReservation(updatedReservation);
        this.loadReservations();
      },
    );
  }

  openExtend(reservation: Reservation): void {
    this.selectedReservation.set(reservation);
    this.dateForm.reset({ expiration: this.localDateTime(new Date(reservation.fechaVencimiento)) });
    this.modal.set('extender');
  }

  extendReservation(): void {
    const reservation = this.selectedReservation();
    if (!reservation || this.dateForm.invalid) return;
    this.runSave(
      this.sales.extendReservation(reservation.id, this.dateForm.controls.expiration.value),
      'Vencimiento actualizado',
      () => {
        this.closeModal();
        this.loadReservations();
      },
    );
  }

  openReservationReason(reservation: Reservation, action: 'cancelar' | 'rechazar'): void {
    this.selectedReservation.set(reservation);
    this.reservationReasonAction.set(action);
    this.reasonForm.reset({ reason: '' });
    this.modal.set('motivo-reserva');
  }

  applyReservationReason(): void {
    const reservation = this.selectedReservation();
    if (!reservation || this.reasonForm.invalid) return;
    const reason = this.reasonForm.controls.reason.value.trim();
    const action = this.reservationReasonAction();
    const request =
      action === 'rechazar'
        ? this.sales.rejectReservationPayment(reservation.id, reason)
        : this.sales.cancelReservation(reservation.id, reason);
    this.runSave(
      request,
      action === 'rechazar'
        ? 'Pago rechazado y lote liberado'
        : 'Reserva cancelada y lote liberado',
      () => {
        this.closeModal();
        this.selectedReservation.set(null);
        this.loadReservations();
      },
    );
  }

  openConvert(reservation: Reservation): void {
    this.selectedReservation.set(reservation);
    this.contractForm.reset({
      type: 'PROMESA_COMPRAVENTA',
      otherConcepts: 0,
      discount: 0,
      downPayment: Number(reservation.valorReserva),
      installments: 12,
      startDate: this.localDate(new Date()),
      endDate: '',
    });
    this.modal.set('convertir');
  }

  convertReservation(): void {
    const reservation = this.selectedReservation();
    if (!reservation || this.contractForm.invalid) return;
    this.runSave(
      this.sales.convertReservation(reservation.id, this.contractPayload()),
      'Contrato creado desde la reserva',
      (contract) => {
        this.closeModal();
        this.selectedReservation.set(null);
        this.loadReservations();
        this.loadContracts();
        this.viewContract(contract);
      },
    );
  }

  viewContract(contract: Contract): void {
    this.selectedReservation.set(null);
    this.loading.set(true);
    this.sales
      .contract(contract.id)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (item) => this.selectedContract.set(item),
        error: (error) => this.showError(error),
      });
  }

  openContractDocument(): void {
    this.selectedFile.set(null);
    this.documentForm.reset({ reason: 'Versión contractual para firma' });
    this.modal.set('documento-contrato');
  }

  attachContractDocument(): void {
    const contract = this.selectedContract();
    const file = this.selectedFile();
    if (!contract || !file || this.documentForm.invalid) {
      this.error.set('Selecciona el PDF o la imagen contractual.');
      return;
    }
    this.runSave(
      this.sales
        .uploadDocument(contract.proyectoId, file)
        .pipe(
          switchMap((document) =>
            this.sales.attachContractDocument(
              contract.id,
              document.id,
              this.documentForm.controls.reason.value.trim(),
            ),
          ),
        ),
      'Nueva versión documental adjuntada',
      () => {
        this.closeModal();
        this.refreshSelectedContract();
      },
    );
  }

  openParticipant(): void {
    this.participantForm.reset({
      customerId: this.activeCustomers()[0]?.id ?? 0,
      role: 'COPROPIETARIO',
      participation: 10,
    });
    this.modal.set('participante');
  }

  addParticipant(): void {
    const contract = this.selectedContract();
    if (!contract || this.participantForm.invalid) return;
    const value = this.participantForm.getRawValue();
    this.runSave(
      this.sales.addContractCustomer(
        contract.id,
        value.customerId,
        value.role,
        value.participation,
      ),
      'Participante agregado',
      () => {
        this.closeModal();
        this.refreshSelectedContract();
      },
    );
  }

  openSigner(): void {
    const contract = this.selectedContract();
    const used = contract?.firmantes.length ?? 0;
    this.signerForm.reset({
      customerId: this.activeCustomers()[0]?.id ?? 0,
      type: 'COPROPIETARIO',
      order: used + 1,
      scheduledAt: '',
    });
    this.modal.set('firmante');
  }

  addSigner(): void {
    const contract = this.selectedContract();
    const customer = this.customerById(this.signerForm.controls.customerId.value);
    if (!contract || !customer || this.signerForm.invalid) return;
    const value = this.signerForm.getRawValue();
    this.runSave(
      this.sales.addSigner(
        contract.id,
        customer.personaId,
        value.type,
        value.order,
        this.nullIfBlank(value.scheduledAt),
      ),
      'Firmante agregado',
      () => {
        this.closeModal();
        this.refreshSelectedContract();
      },
    );
  }

  openSignature(signer: ContractSigner): void {
    this.selectedFile.set(null);
    this.signatureForm.reset({
      signerId: signer.id,
      method: 'ELECTRONICA',
      provider: 'Nexora Core',
      externalReference: '',
      signedAt: '',
    });
    this.modal.set('firma');
  }

  registerSignature(): void {
    const contract = this.selectedContract();
    const file = this.selectedFile();
    if (!contract || !file || this.signatureForm.invalid) {
      this.error.set('Selecciona la evidencia de la firma.');
      return;
    }
    const value = this.signatureForm.getRawValue();
    this.runSave(
      this.sales.uploadDocument(contract.proyectoId, file, 'RESTRINGIDO').pipe(
        switchMap((document) =>
          this.sales.registerSignature(contract.id, value.signerId, {
            metodo: value.method,
            proveedor: this.nullIfBlank(value.provider),
            referenciaExterna: this.nullIfBlank(value.externalReference),
            hashDocumento: document.sha256,
            ipOrigen: null,
            firmadaEn: this.nullIfBlank(value.signedAt),
            estadoValidacion: 'VALIDA',
            archivoFirmaId: document.id,
          }),
        ),
      ),
      'Firma válida registrada',
      (updated) => {
        this.closeModal();
        this.selectedContract.set(updated);
        this.loadContracts();
      },
    );
  }

  startNegotiation(): void {
    const contract = this.selectedContract();
    if (!contract) return;
    this.runSave(
      this.sales.startNegotiation(contract.id),
      'Contrato enviado a negociación',
      (updated) => {
        this.selectedContract.set(updated);
        this.loadContracts();
      },
    );
  }

  sendForSignature(): void {
    const contract = this.selectedContract();
    if (!contract) return;
    this.runSave(
      this.sales.sendForSignature(contract.id),
      'Contrato enviado a firma',
      (updated) => {
        this.selectedContract.set(updated);
        this.loadContracts();
      },
    );
  }

  openCancelContract(): void {
    this.reasonForm.reset({ reason: '' });
    this.modal.set('anular-contrato');
  }

  cancelContract(): void {
    const contract = this.selectedContract();
    if (!contract || this.reasonForm.invalid) return;
    this.runSave(
      this.sales.cancelContract(contract.id, this.reasonForm.controls.reason.value.trim()),
      'Contrato anulado y estado del lote actualizado',
      (updated) => {
        this.closeModal();
        this.selectedContract.set(updated);
        this.loadContracts();
        this.loadReservations();
      },
    );
  }

  downloadDocument(id: number, name: string): void {
    this.sales
      .downloadDocument(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = name;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: (error) => this.showError(error),
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  closeModal(): void {
    this.modal.set(null);
    this.selectedFile.set(null);
    this.error.set('');
  }

  closeDetails(): void {
    this.selectedReservation.set(null);
    this.selectedContract.set(null);
  }

  customerName(id: number): string {
    return this.customerById(id)?.nombreCompleto ?? `Cliente #${id}`;
  }

  customerByPerson(personId: number): Customer | undefined {
    return this.customers().find((customer) => customer.personaId === personId);
  }

  projectName(id: number): string {
    return this.projects().find((project) => project.id === id)?.nombre ?? `Proyecto #${id}`;
  }

  reservationLabel(status: string): string {
    return (
      (
        {
          PENDIENTE_PAGO: 'Pendiente de pago',
          ACTIVA: 'Activa',
          VENCIDA: 'Vencida',
          CANCELADA: 'Cancelada',
          CONVERTIDA: 'Convertida',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  contractLabel(status: string): string {
    return (
      (
        {
          BORRADOR: 'Borrador',
          NEGOCIACION: 'Negociación',
          PENDIENTE_FIRMA: 'Pendiente de firma',
          VIGENTE: 'Vigente',
          FINALIZADO: 'Finalizado',
          ANULADO: 'Anulado',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  readonly contractSteps = [
    { value: 'BORRADOR', label: 'Borrador' },
    { value: 'NEGOCIACION', label: 'Negociación' },
    { value: 'PENDIENTE_FIRMA', label: 'En firma' },
    { value: 'VIGENTE', label: 'Vigente' },
    { value: 'FINALIZADO', label: 'Saldado' },
  ];

  contractStepIndex(status: string): number {
    return this.contractSteps.findIndex((step) => step.value === status);
  }

  contractTypeLabel(type: string): string {
    return ({ PROMESA_COMPRAVENTA: 'Promesa de compraventa', COMPRAVENTA: 'Compraventa', CESION: 'Cesión' } as Record<string, string>)[type] ?? type;
  }

  /** Días que faltan para el vencimiento (negativo si ya venció). */
  daysLeft(date: string): number {
    return Math.floor((new Date(date).getTime() - Date.now()) / 86_400_000);
  }

  daysLeftLabel(date: string): string {
    const days = this.daysLeft(date);
    if (days < 0) return 'Vencida';
    if (days === 0) return 'Vence hoy';
    return days === 1 ? 'Queda 1 día' : `Quedan ${days} días`;
  }

  canUseCustomerLotFlow(): boolean {
    return [
      'CLIENTES_CREAR',
      'PROSPECTOS_CREAR',
      'PROSPECTOS_CALIFICAR',
      'OPORTUNIDADES_CREAR',
      'OPORTUNIDADES_EDITAR',
      'RESERVAS_CREAR',
      'PROYECTOS_VER',
      'LOTES_VER',
    ].every((permission) => this.auth.hasAuthority(permission));
  }

  private loadInitialData(): void {
    this.loading.set(true);
    forkJoin({
      projects: this.projectsService.list({ status: 'ACTIVO', size: 100 }),
      customers: this.auth.hasAuthority('CLIENTES_VER')
        ? this.sales.customers({ size: 100 })
        : of(this.emptyPage<Customer>()),
      reservations: this.auth.hasAuthority('RESERVAS_VER')
        ? this.sales.reservations({ size: 100 })
        : of(this.emptyPage<Reservation>()),
      contracts: this.auth.hasAuthority('CONTRATOS_VER')
        ? this.sales.contracts({ size: 100 })
        : of(this.emptyPage<Contract>()),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ projects, customers, reservations, contracts }) => {
          this.projects.set(projects.contenido);
          this.customers.set(customers.contenido);
          this.reservations.set(reservations.contenido);
          this.contracts.set(contracts.contenido);
        },
        error: (error) => this.showError(error),
      });
  }

  private loadCustomers(): void {
    if (!this.auth.hasAuthority('CLIENTES_VER')) return;
    this.sales
      .customers({ text: this.customerSearch.value.trim(), size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.customers.set(page.contenido),
        error: (error) => this.showError(error),
      });
  }

  private loadReservations(): void {
    if (!this.auth.hasAuthority('RESERVAS_VER')) return;
    this.sales
      .reservations({
        status: this.reservationStatus() as never,
        projectId: this.selectedProjectId(),
        size: 100,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.reservations.set(page.contenido),
        error: (error) => this.showError(error),
      });
  }

  private replaceReservation(updatedReservation: Reservation): void {
    this.reservations.update((reservations) =>
      reservations.map((reservation) =>
        reservation.id === updatedReservation.id ? updatedReservation : reservation,
      ),
    );
  }

  private loadContracts(): void {
    if (!this.auth.hasAuthority('CONTRATOS_VER')) return;
    this.sales
      .contracts({
        status: this.contractStatus() as never,
        projectId: this.selectedProjectId(),
        size: 100,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.contracts.set(page.contenido),
        error: (error) => this.showError(error),
      });
  }

  private loadOpportunities(projectId: number): void {
    this.crm
      .opportunities({ status: 'ABIERTA', projectId, size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.opportunities.set(page.contenido),
        error: (error) => this.showError(error),
      });
  }

  private refreshSelectedContract(): void {
    const contract = this.selectedContract();
    if (!contract) return;
    this.sales
      .contract(contract.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.selectedContract.set(updated);
          this.loadContracts();
        },
        error: (error) => this.showError(error),
      });
  }

  private contractPayload(): ContractConditionsPayload {
    const value = this.contractForm.getRawValue();
    return {
      tipoContrato: value.type,
      otrosConceptos: value.otherConcepts,
      descuento: value.discount,
      cuotaInicial: value.downPayment,
      numeroCuotas: value.installments,
      fechaInicio: value.startDate,
      fechaFinalizacion: this.nullIfBlank(value.endDate),
    };
  }

  private customerById(id: number): Customer | undefined {
    return this.customers().find((customer) => customer.id === id);
  }

  private runSave<T>(
    request: import('rxjs').Observable<T>,
    message: string,
    done: (value: T) => void,
  ): void {
    this.saving.set(true);
    this.clearMessages();
    request
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (value) => {
          this.feedback.set(message);
          done(value);
        },
        error: (error) => this.showError(error),
      });
  }

  private showError(error: unknown): void {
    const response = error as { error?: { detail?: string; title?: string }; message?: string };
    this.error.set(
      response?.error?.detail ??
        response?.error?.title ??
        response?.message ??
        'No fue posible completar la operación.',
    );
  }

  private clearMessages(): void {
    this.feedback.set('');
    this.error.set('');
  }

  private nullIfBlank(value: string): string | null {
    const normalized = value?.trim();
    return normalized ? normalized : null;
  }

  private localDate(date: Date): string {
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
  }

  private localDateTime(date: Date): string {
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
  }

  private emptyPage<T>(): import('../../core/models/api.models').PageResponse<T> {
    return {
      contenido: [],
      pagina: 0,
      tamano: 0,
      totalElementos: 0,
      totalPaginas: 0,
      primera: true,
      ultima: true,
    };
  }
}
