import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin, map, Observable, of, switchMap } from 'rxjs';

import {
  CollectionChannel,
  CollectionManagement,
  FollowUpInstallment,
  Installment,
  PaymentApplication,
  PaymentMethod,
  PaymentPlan,
  PaymentStatus,
  PortfolioConfiguration,
  PortfolioContract,
  PortfolioPayment,
  PortfolioSummary,
} from '../../core/models/portfolio.models';
import { AuthService } from '../../core/services/auth.service';
import { PortfolioService } from '../../core/services/portfolio.service';
import { SalesService } from '../../core/services/sales.service';
import { apiErrorMessage, currentPeriod, NX_UI, ToastService } from '../../shared/ui';
import { PortfolioClosing } from '../../core/models/portfolio.models';
import { ReconciliationTabComponent } from './reconciliation-tab.component';
import { RefinancingTabComponent } from './refinancing-tab.component';
import { ReversalsPanelComponent } from './reversals-panel.component';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';

type PortfolioTab = 'contratos' | 'pagos' | 'mora' | 'refinanciaciones' | 'conciliacion';
type PortfolioModal = 'pago' | 'cobro' | 'configuracion' | 'motivo' | null;
type ReasonAction = 'rechazar' | 'reversion';
type CollectionView = 'formulario' | 'historial';

@Component({
  selector: 'app-portfolio-page',
  imports: [CommonModule, ReactiveFormsModule, ...NX_UI, MoneyInputDirective, RefinancingTabComponent, ReconciliationTabComponent, ReversalsPanelComponent],
  templateUrl: './portfolio.page.html',
  styleUrl: './portfolio.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioPage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly portfolio = inject(PortfolioService);
  private readonly sales = inject(SalesService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeTab = signal<PortfolioTab>('contratos');
  private readonly toast = inject(ToastService);
  /** Cierre de mes: periodo, resultado y estado del diálogo. */
  readonly closingOpen = signal(false);
  readonly closingPeriod = signal(this.previousPeriod());
  readonly closingResult = signal<PortfolioClosing | null>(null);
  readonly closingBusy = signal(false);
  readonly reversalKey = signal(0);
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly contracts = signal<PortfolioContract[]>([]);
  readonly payments = signal<PortfolioPayment[]>([]);
  readonly followUpItems = signal<FollowUpInstallment[]>([]);
  readonly methods = signal<PaymentMethod[]>([]);
  readonly configuration = signal<PortfolioConfiguration | null>(null);
  readonly plans = signal<PaymentPlan[]>([]);
  readonly collections = signal<CollectionManagement[]>([]);
  readonly collectionsLoading = signal(false);
  readonly collectionView = signal<CollectionView>('formulario');
  readonly selectedContract = signal<PortfolioContract | null>(null);
  readonly selectedPayment = signal<PortfolioPayment | null>(null);
  readonly selectedFollowUp = signal<FollowUpInstallment | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly modal = signal<PortfolioModal>(null);
  readonly reasonAction = signal<ReasonAction>('rechazar');
  readonly loading = signal(true);
  readonly detailLoading = signal(false);
  readonly saving = signal(false);
  readonly feedback = signal('');
  readonly error = signal('');
  readonly paymentStatus = signal<PaymentStatus | ''>('');
  readonly contractSearch = signal('');

  readonly activePlan = computed(() => this.plans().find((plan) => plan.estado === 'ACTIVO') ?? null);
  readonly filteredContracts = computed(() => {
    const query = this.contractSearch().trim().toLowerCase();
    if (!query) return this.contracts();
    return this.contracts().filter((contract) =>
      [
        contract.contratoCodigo,
        contract.proyectoNombre,
        contract.clienteNombre,
        contract.clienteDocumento,
        contract.loteCodigo,
      ].some((value) => value.toLowerCase().includes(query)),
    );
  });
  readonly selectedMethod = computed(() =>
    this.methods().find((method) => method.id === Number(this.paymentForm.controls.methodId.value)) ?? null,
  );
  readonly selectedCollections = computed(() => {
    const installment = this.selectedFollowUp();
    if (!installment) return [];
    return this.collections().filter((management) => management.cuotaId === installment.cuotaId);
  });

  readonly paymentForm = this.fb.nonNullable.group({
    methodId: [0, [Validators.required, Validators.min(1)]],
    paidAt: [this.localDateTime(), Validators.required],
    amount: [0, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
    reference: ['', Validators.maxLength(180)],
    observations: ['', Validators.maxLength(2000)],
  });

  readonly collectionForm = this.fb.nonNullable.group({
    channel: ['LLAMADA' as CollectionChannel, Validators.required],
    result: ['', [Validators.required, Validators.maxLength(120)]],
    detail: ['', Validators.maxLength(2000)],
    promiseDate: [''],
    promiseAmount: [0, [Validators.min(0), Validators.pattern(/^\d+$/)]],
  });

  readonly configurationForm = this.fb.nonNullable.group({
    annualRate: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    overdueRate: [0, [Validators.required, Validators.min(0), Validators.max(100)]],
    frequency: ['MENSUAL' as PortfolioConfiguration['periodicidad'], Validators.required],
    firstInstallmentDays: [30, [Validators.required, Validators.min(0), Validators.max(365)]],
    automatic: [true],
  });

  readonly reasonForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    if (!this.auth.hasAuthority('CUOTAS_VER') && this.auth.hasAuthority('PAGOS_VER')) {
      this.activeTab.set('pagos');
    }
    this.loadAll();
  }

  openClosing(): void {
    this.closingResult.set(null);
    this.closingPeriod.set(this.previousPeriod());
    this.closingOpen.set(true);
  }

  closePeriod(): void {
    this.closingBusy.set(true);
    this.portfolio.closePeriod(this.closingPeriod()).subscribe({
      next: (result) => {
        this.closingBusy.set(false);
        this.closingResult.set(result);
        this.toast.success(`Cartera de ${result.periodo} cerrada.`);
      },
      error: (e) => {
        this.closingBusy.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo cerrar el periodo.'));
      },
    });
  }

  collectionTitle(): string {
    const item = this.selectedFollowUp();
    if (this.collectionView() === 'historial') return 'HISTORIAL DE GESTIONES';
    return item?.estado === 'VENCIDA' ? 'GESTIÓN DE COBRANZA' : 'GESTIÓN PREVENTIVA';
  }

  reloadAfterReversal(): void {
    this.loadAll();
  }

  /** Mes anterior (el que normalmente se cierra) como AAAA-MM. */
  private previousPeriod(): string {
    const d = new Date();
    return currentPeriod(new Date(d.getFullYear(), d.getMonth() - 1, 1));
  }

  setTab(tab: PortfolioTab): void {
    this.activeTab.set(tab);
    this.clearMessages();
  }

  filterPayments(status: string): void {
    this.paymentStatus.set(status as PaymentStatus | '');
    this.loadPayments();
  }

  searchContracts(value: string): void {
    this.contractSearch.set(value);
  }

  viewContract(contract: PortfolioContract): void {
    this.selectedContract.set(contract);
    this.loadPlans(contract.contratoId);
  }

  closeDetails(): void {
    if (this.saving()) return;
    this.selectedContract.set(null);
    this.plans.set([]);
    this.collections.set([]);
  }

  synchronizePlan(contract = this.selectedContract()): void {
    if (!contract || !this.auth.hasAuthority('CUOTAS_PROGRAMAR')) return;
    this.saving.set(true);
    this.clearMessages();
    this.portfolio
      .synchronizePlan(contract.contratoId)
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (plan) => {
          this.plans.set([plan]);
          this.feedback.set('La cartera del contrato se generó y quedó activa.');
          this.refreshOperationalData();
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openPayment(contract: PortfolioContract): void {
    this.selectedContract.set(contract);
    this.selectedFile.set(null);
    this.paymentForm.reset({
      methodId: this.methods()[0]?.id ?? 0,
      paidAt: this.localDateTime(),
      amount: 0,
      reference: '',
      observations: '',
    });
    const open = (plans: PaymentPlan[]) => {
      const active = plans.find((plan) => plan.estado === 'ACTIVO');
      if (!active) {
        this.error.set('Primero debes generar el plan de pagos del contrato.');
        return;
      }
      this.modal.set('pago');
    };
    if (this.activePlan()?.contratoId === contract.contratoId) {
      open(this.plans());
    } else {
      this.detailLoading.set(true);
      this.portfolio
        .plans(contract.contratoId)
        .pipe(finalize(() => this.detailLoading.set(false)), takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (plans) => {
            this.plans.set(plans);
            open(plans);
          },
          error: (error) => this.error.set(this.errorDetail(error)),
        });
    }
  }

  selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.item(0) ?? null);
  }

  savePayment(): void {
    const contract = this.selectedContract();
    const plan = this.activePlan();
    const method = this.selectedMethod();
    if (!contract || !plan || !method || this.paymentForm.invalid) {
      this.paymentForm.markAllAsTouched();
      return;
    }
    const value = this.paymentForm.getRawValue();
    if (method.requiereReferencia && !value.reference.trim()) {
      this.error.set('El método seleccionado exige una referencia.');
      return;
    }
    if (method.requiereComprobante && !this.selectedFile()) {
      this.error.set('El método seleccionado exige un comprobante.');
      return;
    }
    const applications = this.allocate(value.amount, plan.cuotas);
    if (!applications) return;

    this.saving.set(true);
    this.clearMessages();
    this.uploadReceipt(contract)
      .pipe(
        switchMap((receiptId) =>
          this.portfolio.registerPayment({
            contratoId: contract.contratoId,
            reservaId: null,
            clienteId: contract.clienteId,
            metodoPagoId: method.id,
            fechaPago: value.paidAt,
            valor: this.money(value.amount),
            referencia: this.nullIfBlank(value.reference),
            comprobanteId: receiptId,
            origen: 'MANUAL',
            observaciones: this.nullIfBlank(value.observations),
          }),
        ),
        switchMap((payment) =>
          this.auth.hasAuthority('PAGOS_CONFIRMAR')
            ? this.portfolio.confirmPayment(payment.id, applications, 'Pago confirmado desde Cartera')
            : this.portfolio.submitPayment(payment.id),
        ),
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (payment) => {
          this.modal.set(null);
          this.feedback.set(
            payment.estado === 'CONFIRMADO'
              ? 'El abono quedó confirmado y aplicado al plan.'
              : 'El abono quedó enviado a revisión.',
          );
          this.refreshOperationalData(contract.contratoId);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  confirmExisting(payment: PortfolioPayment): void {
    if (!payment.contratoId || !this.auth.hasAuthority('PAGOS_CONFIRMAR')) return;
    const contract = this.contracts().find((item) => item.contratoId === payment.contratoId);
    if (!contract) {
      this.error.set('El contrato del pago no está disponible en tu alcance.');
      return;
    }
    this.saving.set(true);
    this.portfolio
      .plans(contract.contratoId)
      .pipe(
        switchMap((plans) => {
          const plan = plans.find((item) => item.estado === 'ACTIVO');
          const applications = plan ? this.allocate(payment.valor, plan.cuotas, false) : null;
          if (!applications) throw new Error('No existe saldo suficiente para aplicar el pago.');
          return this.portfolio.confirmPayment(payment.id, applications, 'Pago revisado y confirmado');
        }),
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.feedback.set('El pago quedó confirmado y aplicado.');
          this.refreshOperationalData(contract.contratoId);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openReason(payment: PortfolioPayment, action: ReasonAction): void {
    this.selectedPayment.set(payment);
    this.reasonAction.set(action);
    this.reasonForm.reset({ reason: '' });
    this.modal.set('motivo');
  }

  saveReason(): void {
    const payment = this.selectedPayment();
    if (!payment || this.reasonForm.invalid) {
      this.reasonForm.markAllAsTouched();
      return;
    }
    const reason = this.reasonForm.controls.reason.value.trim();
    const request = this.reasonAction() === 'rechazar'
      ? this.portfolio.rejectPayment(payment.id, reason)
      : this.portfolio.requestReversal(payment.id, reason);
    this.saving.set(true);
    request
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.modal.set(null);
          this.feedback.set(
            this.reasonAction() === 'rechazar'
              ? 'El pago fue rechazado con trazabilidad.'
              : 'La reversión quedó solicitada para aprobación independiente.',
          );
          this.loadPayments();
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  updateOverdue(): void {
    this.saving.set(true);
    this.portfolio
      .updateOverdue()
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.feedback.set(`Mora actualizada en ${updated} cuota(s).`);
          this.loadFollowUp();
          this.refreshOperationalData();
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openCollection(item: FollowUpInstallment, view: CollectionView = 'formulario'): void {
    this.clearMessages();
    this.selectedFollowUp.set(item);
    this.collectionView.set(view);
    this.resetCollectionForm();
    this.collections.set([]);
    this.collectionsLoading.set(true);
    this.portfolio
      .collections(item.contratoId)
      .pipe(
        finalize(() => this.collectionsLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (collections) => this.collections.set(collections),
        error: (error) => this.error.set(this.errorDetail(error)),
      });
    this.modal.set('cobro');
  }

  showCollectionForm(): void {
    this.clearMessages();
    this.resetCollectionForm();
    this.collectionView.set('formulario');
  }

  saveCollection(): void {
    const item = this.selectedFollowUp();
    if (!item || this.collectionForm.invalid) {
      this.collectionForm.markAllAsTouched();
      return;
    }
    const value = this.collectionForm.getRawValue();
    if (!value.result.trim()) {
      this.error.set('Registra el resultado de la gestión.');
      return;
    }
    const hasPromiseDate = Boolean(value.promiseDate);
    const hasPromiseAmount = value.promiseAmount > 0;
    if (hasPromiseDate !== hasPromiseAmount) {
      this.error.set('La promesa requiere fecha y valor. Completa ambos campos o déjalos vacíos.');
      return;
    }
    if (hasPromiseDate && value.promiseDate < this.localDate()) {
      this.error.set('La fecha de la promesa no puede estar en el pasado.');
      return;
    }
    if (hasPromiseAmount && this.money(value.promiseAmount) > this.money(item.totalPendiente)) {
      this.error.set('El valor prometido no puede superar el saldo pendiente de la cuota.');
      return;
    }
    this.saving.set(true);
    this.portfolio
      .createCollection({
        contratoId: item.contratoId,
        cuotaId: item.cuotaId,
        canal: value.channel,
        resultado: value.result.trim(),
        detalle: this.nullIfBlank(value.detail),
        promesaPagoFecha: this.nullIfBlank(value.promiseDate),
        promesaPagoValor: value.promiseAmount > 0 ? this.money(value.promiseAmount) : null,
      })
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.collections.update((items) => [created, ...items.filter((item) => item.id !== created.id)]);
          this.feedback.set(
            item.estado === 'VENCIDA'
              ? 'La gestión de cobro quedó registrada.'
              : 'La gestión preventiva quedó registrada.',
          );
          this.collectionView.set('historial');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openConfiguration(): void {
    const configuration = this.configuration();
    if (!configuration) return;
    this.configurationForm.reset({
      annualRate: configuration.tasaEfectivaAnual * 100,
      overdueRate: configuration.tasaMoraEfectivaAnual * 100,
      frequency: configuration.periodicidad,
      firstInstallmentDays: configuration.diasPrimeraCuota,
      automatic: configuration.autoGenerarPlan,
    });
    this.modal.set('configuracion');
  }

  saveConfiguration(): void {
    if (this.configurationForm.invalid) {
      this.configurationForm.markAllAsTouched();
      return;
    }
    const value = this.configurationForm.getRawValue();
    this.saving.set(true);
    this.portfolio
      .updateConfiguration({
        tasaEfectivaAnual: value.annualRate / 100,
        tasaMoraEfectivaAnual: value.overdueRate / 100,
        periodicidad: value.frequency,
        diasPrimeraCuota: value.firstInstallmentDays,
        autoGenerarPlan: value.automatic,
      })
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (configuration) => {
          this.configuration.set(configuration);
          this.modal.set(null);
          this.feedback.set('La configuración de cartera quedó actualizada.');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  closeModal(): void {
    if (!this.saving()) this.modal.set(null);
  }

  exportPortfolio(): void {
    const rows = [
      ['Contrato', 'Proyecto', 'Cliente', 'Documento', 'Lote', 'Programado', 'Pagado', 'Saldo', 'Vencido', 'Proximo vencimiento'],
      ...this.filteredContracts().map((item) => [
        item.contratoCodigo,
        item.proyectoNombre,
        item.clienteNombre,
        item.clienteDocumento,
        item.loteCodigo,
        item.totalProgramado,
        item.totalPagado,
        item.saldoPendiente,
        item.saldoVencido,
        item.proximoVencimiento ?? '',
      ]),
    ];
    this.downloadCsv('cartera-nexora.csv', rows);
  }

  exportStatement(): void {
    const contract = this.selectedContract();
    const plan = this.activePlan();
    if (!contract || !plan) return;
    const rows = [
      ['Contrato', contract.contratoCodigo],
      ['Cliente', contract.clienteNombre],
      ['Proyecto', contract.proyectoNombre],
      [],
      ['Cuota', 'Vencimiento', 'Programado', 'Pagado', 'Saldo', 'Mora', 'Dias mora', 'Estado'],
      ...plan.cuotas.map((item) => [
        item.numero,
        item.fechaVencimiento,
        item.valorProgramado,
        item.valorPagado,
        item.saldo,
        item.interesMora,
        item.diasMora,
        item.estado,
      ]),
    ];
    this.downloadCsv(`estado-cuenta-${contract.contratoCodigo}.csv`, rows);
  }

  contractCode(id: number | null): string {
    return this.contracts().find((item) => item.contratoId === id)?.contratoCodigo ?? `#${id ?? '-'}`;
  }

  contractClient(id: number): string {
    return this.contracts().find((item) => item.contratoId === id)?.clienteNombre ?? `Contrato #${id}`;
  }

  methodName(id: number): string {
    return this.methods().find((method) => method.id === id)?.nombre ?? `Método #${id}`;
  }

  installmentLabel(status: string): string {
    return status.replaceAll('_', ' ');
  }

  private loadAll(): void {
    this.loading.set(true);
    forkJoin({
      summary: this.auth.hasAuthority('CUOTAS_VER') ? this.portfolio.summary() : of(null),
      contracts: this.auth.hasAuthority('CUOTAS_VER') ? this.portfolio.contracts() : of([]),
      payments: this.auth.hasAuthority('PAGOS_VER')
        ? this.portfolio.payments().pipe(map((page) => page.contenido))
        : of([]),
      followUp: this.auth.hasAuthority('MORA_VER')
        ? this.portfolio.followUpInstallments().pipe(map((page) => page.contenido))
        : of([]),
      methods: this.auth.hasAuthority('PAGOS_CREAR') ? this.portfolio.paymentMethods() : of([]),
      configuration: this.auth.hasAuthority('CUOTAS_PROGRAMAR')
        ? this.portfolio.configuration()
        : of(null),
    })
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.summary.set(data.summary);
          this.contracts.set(data.contracts);
          this.payments.set(data.payments);
          this.followUpItems.set(data.followUp);
          this.methods.set(data.methods);
          this.configuration.set(data.configuration);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private refreshOperationalData(contractId?: number): void {
    forkJoin({ summary: this.portfolio.summary(), contracts: this.portfolio.contracts() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ summary, contracts }) => {
          this.summary.set(summary);
          this.contracts.set(contracts);
          if (contractId) {
            const selected = contracts.find((item) => item.contratoId === contractId) ?? null;
            this.selectedContract.set(selected);
            if (selected) this.loadPlans(contractId);
          }
          this.loadPayments();
          if (this.auth.hasAuthority('MORA_VER')) this.loadFollowUp();
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private loadPlans(contractId: number): void {
    this.detailLoading.set(true);
    this.portfolio
      .plans(contractId)
      .pipe(finalize(() => this.detailLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (plans) => this.plans.set(plans),
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private loadPayments(): void {
    if (!this.auth.hasAuthority('PAGOS_VER')) return;
    this.portfolio
      .payments({ status: this.paymentStatus() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.payments.set(page.contenido);
          this.reversalKey.update((k) => k + 1);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private loadFollowUp(): void {
    this.portfolio
      .followUpInstallments()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.followUpItems.set(page.contenido),
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private resetCollectionForm(): void {
    this.collectionForm.reset({
      channel: 'LLAMADA',
      result: '',
      detail: '',
      promiseDate: '',
      promiseAmount: 0,
    });
  }

  private uploadReceipt(contract: PortfolioContract): Observable<number | null> {
    const file = this.selectedFile();
    return file
      ? this.sales.uploadDocument(contract.proyectoId, file, 'CONFIDENCIAL').pipe(map((document) => document.id))
      : of(null);
  }

  private allocate(amount: number, installments: Installment[], reportError = true): PaymentApplication[] | null {
    let remaining = this.money(amount);
    const applications: PaymentApplication[] = [];
    const available = installments
      .filter((item) => ['PENDIENTE', 'PARCIAL', 'VENCIDA'].includes(item.estado))
      .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento));
    for (const installment of available) {
      if (remaining <= 0) break;
      const overdue = Math.min(remaining, this.money(installment.interesMora));
      if (overdue > 0) {
        applications.push({ id: 0, cuotaId: installment.id, facturaId: null, concepto: 'MORA', valor: overdue });
        remaining = this.money(remaining - overdue);
      }
      const capital = Math.min(remaining, this.money(installment.saldo));
      if (capital > 0) {
        applications.push({ id: 0, cuotaId: installment.id, facturaId: null, concepto: 'CAPITAL', valor: capital });
        remaining = this.money(remaining - capital);
      }
    }
    if (remaining > 0) {
      if (reportError) this.error.set('El valor supera el saldo disponible del plan.');
      return null;
    }
    return applications;
  }

  private downloadCsv(filename: string, rows: Array<Array<string | number>>): void {
    const csv = rows
      .map((row) => row.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(','))
      .join('\n');
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private clearMessages(): void {
    this.feedback.set('');
    this.error.set('');
  }

  private errorDetail(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.detail ?? error.error?.title ?? 'No fue posible completar la operación.';
    }
    return error instanceof Error ? error.message : 'No fue posible completar la operación.';
  }

  private localDateTime(): string {
    const date = new Date();
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
  }

  private localDate(): string {
    return this.localDateTime().slice(0, 10);
  }

  private nullIfBlank(value: string): string | null {
    const normalized = value.trim();
    return normalized ? normalized : null;
  }

  private money(value: number): number {
    return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
  }
}
