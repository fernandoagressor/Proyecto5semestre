import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, finalize, map, Observable, of, switchMap } from 'rxjs';

import { Lot } from '../../core/models/inventory.models';
import { Customer, Reservation } from '../../core/models/sales.models';
import { AuthService } from '../../core/services/auth.service';
import { CustomerLotFlowService } from '../../core/services/customer-lot-flow.service';
import { SalesService } from '../../core/services/sales.service';
import { PhoneInputComponent, phoneValidator } from '../../shared/components/phone-input.component';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, NX_UI } from '../../shared/ui';

/** Resultado de reservar: la reserva, el nombre del cliente y si el pago quedó confirmado. */
export interface LotReserved {
  reserva: Reservation;
  cliente: string;
  pagoConfirmado: boolean;
  /** Al terminar, seguir de inmediato con el contrato (solo si el pago quedó confirmado). */
  generarContrato?: boolean;
}

/**
 * Reservar el lote que ya está elegido, en dos pasos: 1) datos del cliente (si su documento ya
 * existe, se usa ese cliente) y 2) condiciones y pago de la reserva, con los datos del lote ya
 * puestos. Al reservar, el lote queda RESERVADO de inmediato y nadie más puede reservarlo hasta
 * que la reserva se cancele o venza.
 */
@Component({
  selector: 'app-reserve-lot',
  imports: [ReactiveFormsModule, RouterLink, ...NX_UI, MoneyInputDirective, PhoneInputComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reserve-lot.component.html',
  styleUrl: './reserve-lot.component.scss',
})
export class ReserveLotComponent {
  private readonly fb = inject(FormBuilder);
  private readonly flow = inject(CustomerLotFlowService);
  private readonly sales = inject(SalesService);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly lot = input.required<Lot>();
  readonly stageName = input('');
  readonly blockName = input('');
  /** Se emite al terminar (con la reserva) o al cancelar (null). */
  readonly done = output<LotReserved | null>();

  readonly step = signal<1 | 2 | 3>(1);
  readonly busy = signal(false);
  readonly error = signal('');
  /** Cliente que ya existía con el documento escrito. */
  readonly existing = signal<Customer | null>(null);
  readonly searching = signal(false);
  readonly result = signal<LotReserved | null>(null);
  readonly receipt = signal<File | null>(null);
  readonly canConfirmPayment = this.auth.hasAuthority('PAGOS_CONFIRMAR');
  readonly canConvert = this.auth.hasAuthority('RESERVAS_CONVERTIR_CONTRATO');

  readonly customerForm = this.fb.nonNullable.group({
    tipoDocumento: ['CC', Validators.required],
    numeroDocumento: ['', [Validators.required, Validators.maxLength(40), Validators.pattern(/^[0-9A-Za-z-]+$/)]],
    nombres: ['', [Validators.required, Validators.maxLength(120)]],
    apellidos: ['', Validators.maxLength(120)],
    telefono: ['', [Validators.maxLength(40), phoneValidator]],
    correo: ['', [Validators.email, Validators.maxLength(180)]],
  }, { validators: (group: AbstractControl): ValidationErrors | null => {
    const tel = group.get('telefono');
    const mail = group.get('correo');
    const sinContacto = !tel?.value?.trim() && !mail?.value?.trim();
    // El mensaje se muestra en el campo correo; se limpia en cuanto hay teléfono o correo.
    if (mail && sinContacto && !mail.hasError('email')) mail.setErrors({ server: 'Escribe al menos un teléfono o un correo.' });
    else if (mail?.hasError('server')) mail.setErrors(null);
    return null;
  } });

  readonly reservationForm = this.fb.nonNullable.group({
    valorReserva: [null as number | null, [Validators.required, Validators.min(1)]],
    formaPago: ['TRANSFERENCIA', Validators.required],
    fechaVencimiento: [this.localDateTime(new Date(Date.now() + 48 * 3_600_000)), Validators.required],
    pagado: [false],
  });

  readonly minExpiry = this.localDateTime(new Date(Date.now() + 5 * 60_000));
  readonly pricePerM2 = computed(() => {
    const lot = this.lot();
    return lot.areaM2 ? Math.round(Number(lot.valorLista) / Number(lot.areaM2)) : null;
  });

  /** Al salir del número de documento, busca si esa persona ya es cliente de la empresa. */
  lookupDocument(): void {
    const { tipoDocumento, numeroDocumento } = this.customerForm.getRawValue();
    const numero = numeroDocumento.trim();
    this.existing.set(null);
    this.setCustomerFieldsEnabled(true);
    if (!numero || !this.auth.hasAuthority('CLIENTES_VER')) return;
    this.searching.set(true);
    this.sales
      .customers({ text: numero, size: 10 })
      .pipe(
        map((page) => page.contenido.find((c) => c.numeroDocumento === numero && c.tipoDocumento === tipoDocumento) ?? null),
        catchError(() => of(null)),
        finalize(() => this.searching.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((customer) => {
        this.existing.set(customer);
        if (customer) {
          this.customerForm.patchValue({
            nombres: customer.nombres,
            apellidos: customer.apellidos ?? '',
            telefono: customer.telefono ?? '',
            correo: customer.correo ?? '',
          });
          this.setCustomerFieldsEnabled(false);
        }
      });
  }

  toConditions(): void {
    this.error.set('');
    const existing = this.existing();
    if (existing && existing.estado !== 'ACTIVO') {
      this.error.set(`${existing.nombreCompleto} está ${existing.estado.toLowerCase()} en Clientes; actívalo para reservar.`);
      return;
    }
    if (!existing && this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      return;
    }
    this.step.set(2);
  }

  setReceipt(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.receipt.set(file);
  }

  reserve(): void {
    const lot = this.lot();
    const conditions = this.reservationForm.getRawValue();
    this.error.set('');
    if (this.reservationForm.invalid || this.busy()) {
      this.reservationForm.markAllAsTouched();
      return;
    }
    if (Number(conditions.valorReserva) > Number(lot.valorLista)) {
      this.reservationForm.controls.valorReserva.setErrors({ server: 'No puede superar el precio del lote.' });
      this.reservationForm.controls.valorReserva.markAsTouched();
      return;
    }
    if (new Date(conditions.fechaVencimiento).getTime() <= Date.now()) {
      this.reservationForm.controls.fechaVencimiento.setErrors({ server: 'Debe ser una fecha y hora futura.' });
      this.reservationForm.controls.fechaVencimiento.markAsTouched();
      return;
    }
    const existing = this.existing();
    const customer = this.customerForm.getRawValue();
    const name = existing?.nombreCompleto ?? `${customer.nombres} ${customer.apellidos}`.trim();
    const confirmNow = this.canConfirmPayment && conditions.pagado;
    this.busy.set(true);
    this.flow
      .create({
        clienteId: existing?.id ?? null,
        cliente: existing ? null : {
          tipoPersona: customer.tipoDocumento === 'NIT' ? 'JURIDICA' : 'NATURAL',
          tipoDocumento: customer.tipoDocumento,
          numeroDocumento: customer.numeroDocumento.trim(),
          nombres: customer.nombres.trim(),
          apellidos: customer.apellidos.trim() || null,
          telefono: customer.telefono.trim() || null,
          correo: customer.correo.trim() || null,
          direccion: null,
          origen: 'VENTAS',
          observaciones: null,
        },
        proyectoId: lot.proyectoId,
        loteId: lot.id,
        fechaVencimiento: conditions.fechaVencimiento,
        valorReserva: Number(conditions.valorReserva),
        formaPago: conditions.formaPago,
      })
      .pipe(
        switchMap(({ reserva }) => (confirmNow ? this.confirmPayment(reserva) : of({ reserva, pagoConfirmado: false }))),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ reserva, pagoConfirmado }) => {
          this.result.set({ reserva, cliente: name, pagoConfirmado });
          this.step.set(3);
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'No se pudo reservar el lote.')),
      });
  }

  close(): void {
    this.done.emit(this.result());
  }

  /** Cierra la reserva y abre "Generar contrato" con esta misma reserva. */
  continueToContract(): void {
    const result = this.result();
    if (result) this.done.emit({ ...result, generarContrato: true });
  }

  money(value: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(Number(value ?? 0));
  }

  dateTime(value: string): string {
    return new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
  }

  /**
   * Sube el comprobante (si hay) y confirma el pago. Si algo falla aquí la reserva ya existe y el
   * lote ya está reservado: se informa que el pago queda pendiente en Ventas.
   */
  private confirmPayment(reserva: Reservation): Observable<{ reserva: Reservation; pagoConfirmado: boolean }> {
    const file = this.receipt();
    const upload: Observable<number | null> = file
      ? this.sales.uploadDocument(reserva.proyectoId, file).pipe(map((d) => d.id))
      : of(null);
    return upload.pipe(
      switchMap((archivoId) => this.sales.confirmReservationPayment(reserva.id, archivoId)),
      map((confirmed) => ({ reserva: confirmed, pagoConfirmado: true })),
      catchError(() => of({ reserva, pagoConfirmado: false })),
    );
  }

  private setCustomerFieldsEnabled(enabled: boolean): void {
    for (const name of ['nombres', 'apellidos', 'telefono', 'correo'] as const) {
      const control = this.customerForm.controls[name];
      if (enabled) control.enable({ emitEvent: false });
      else control.disable({ emitEvent: false });
    }
  }

  private localDateTime(date: Date): string {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
    return local.toISOString().slice(0, 16);
  }
}
