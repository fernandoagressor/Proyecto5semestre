import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, finalize, map, of, startWith, switchMap, tap } from 'rxjs';

import { Contract, ContractConditionsPayload, ContractPreview, ContractType, Reservation } from '../../core/models/sales.models';
import { AuthService } from '../../core/services/auth.service';
import { SalesService } from '../../core/services/sales.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, NX_UI } from '../../shared/ui';
import { todayIso } from '../../shared/ui/format';

/** Resultado de generar el contrato: el contrato y si quedó firmado (venta activa). */
export interface ContractCreated {
  contrato: Contract;
  firmado: boolean;
  /** Quedó en el portal del cliente para que lo firme desde su casa. */
  enviadoAlCliente?: boolean;
}

/**
 * Generar el contrato de una reserva pagada, en tres pasos:
 * 1) condiciones, con el cliente y el lote ya puestos y un resumen en vivo del plan de pagos
 *    (el mismo cálculo que hará Cartera, incluida la cuota 0 de la cuota inicial pendiente);
 * 2) firma: en la oficina (se sube el contrato firmado y la venta queda activa) o después;
 * 3) listo.
 */
@Component({
  selector: 'app-create-contract',
  imports: [ReactiveFormsModule, RouterLink, ...NX_UI, MoneyInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './create-contract.component.html',
  styleUrl: './create-contract.component.scss',
})
export class CreateContractComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly sales = inject(SalesService);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly reservation = input.required<Reservation>();
  readonly lotCode = input('');
  readonly customerName = input('');
  /** Se emite al terminar (con el contrato) o al cancelar antes de crearlo (null). */
  readonly done = output<ContractCreated | null>();

  readonly step = signal<1 | 2 | 3>(1);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly preview = signal<ContractPreview | null>(null);
  readonly previewError = signal('');
  readonly calculating = signal(false);
  readonly showSchedule = signal(false);
  readonly showExtras = signal(false);
  readonly contract = signal<Contract | null>(null);
  readonly signed = signal(false);
  readonly signedFile = signal<File | null>(null);
  readonly canSign = this.auth.hasAuthority('CONTRATOS_FIRMAS_GESTIONAR');
  /** Enviar al portal exige además adjuntar la versión del contrato (CONTRATOS_EDITAR). */
  readonly canSendToClient = this.canSign && this.auth.hasAuthority('CONTRATOS_EDITAR');
  /** Cómo se firma: en la oficina o el cliente desde su casa (portal). */
  readonly signMode = signal<'oficina' | 'portal'>('oficina');
  /** El contrato quedó enviado al portal del cliente para que lo firme. */
  readonly sentToClient = signal(false);

  readonly form = this.fb.nonNullable.group({
    tipoContrato: ['PROMESA_COMPRAVENTA' as ContractType, Validators.required],
    cuotaInicial: [0 as number | null, [Validators.required, Validators.min(0)]],
    numeroCuotas: [12, [Validators.required, Validators.min(1), Validators.max(360)]],
    fechaInicio: [todayIso(), Validators.required],
    descuento: [0 as number | null, [Validators.min(0)]],
    otrosConceptos: [0 as number | null, [Validators.min(0)]],
  });

  readonly regularInstallments = computed(() => (this.preview()?.cuotas ?? []).filter((c) => c.numero > 0).length);

  ngOnInit(): void {
    this.form.controls.cuotaInicial.setValue(Number(this.reservation().valorReserva));
    this.form.valueChanges
      .pipe(
        startWith(this.form.getRawValue()),
        debounceTime(350),
        tap(() => this.calculating.set(true)),
        switchMap(() => {
          if (this.form.invalid) return of({ preview: null, error: '' });
          return this.sales.contractPreview(this.reservation().id, this.payload()).pipe(
            map((preview) => ({ preview, error: '' })),
            catchError((e) => of({ preview: null, error: apiErrorMessage(e, 'No se pudo calcular el plan.') })),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ preview, error }) => {
        this.calculating.set(false);
        this.previewError.set(error);
        this.preview.set(preview);
      });
  }

  createContract(): void {
    this.error.set('');
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.previewError()) {
      this.error.set(this.previewError());
      return;
    }
    this.busy.set(true);
    this.sales
      .convertReservation(this.reservation().id, this.payload())
      .pipe(finalize(() => this.busy.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (contract) => {
          this.contract.set(contract);
          this.step.set(2);
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'No se pudo crear el contrato.')),
      });
  }

  setSignedFile(event: Event): void {
    this.signedFile.set((event.target as HTMLInputElement).files?.[0] ?? null);
  }

  /** Sube el contrato firmado y activa la venta en un solo paso. */
  signNow(): void {
    const contract = this.contract();
    const file = this.signedFile();
    this.error.set('');
    if (!contract || this.busy()) return;
    if (!file) {
      this.error.set('Adjunta el contrato firmado (PDF o foto).');
      return;
    }
    this.busy.set(true);
    this.sales
      .uploadDocument(contract.proyectoId, file)
      .pipe(
        switchMap((document) => this.sales.signInPerson(contract.id, document.id)),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.contract.set(updated);
          this.signed.set(true);
          this.step.set(3);
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'No se pudo registrar la firma.')),
      });
  }

  /** Sube el contrato para firmar y lo envía al portal del cliente. */
  sendToClient(): void {
    const contract = this.contract();
    const file = this.signedFile();
    this.error.set('');
    if (!contract || this.busy()) return;
    if (!file) {
      this.error.set('Adjunta el contrato para firmar (PDF).');
      return;
    }
    this.busy.set(true);
    this.sales
      .uploadDocument(contract.proyectoId, file)
      .pipe(
        switchMap((document) => this.sales.attachContractDocument(contract.id, document.id, 'Contrato para firma del cliente')),
        switchMap(() => this.sales.sendForSignature(contract.id)),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.contract.set(updated);
          this.sentToClient.set(true);
          this.signed.set(false);
          this.step.set(3);
        },
        error: (e) => this.error.set(apiErrorMessage(e, 'No se pudo enviar el contrato al cliente.')),
      });
  }

  setSignMode(mode: 'oficina' | 'portal'): void {
    this.signMode.set(mode);
    this.signedFile.set(null);
    this.error.set('');
  }

  signLater(): void {
    this.signed.set(false);
    this.step.set(3);
  }

  close(): void {
    const contract = this.contract();
    this.done.emit(contract ? { contrato: contract, firmado: this.signed(), enviadoAlCliente: this.sentToClient() } : null);
  }

  money(value: number | null | undefined): string {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(Number(value ?? 0));
  }

  date(value: string | null | undefined): string {
    if (!value) return '—';
    const [y, m, d] = value.slice(0, 10).split('-').map(Number);
    return new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' }).format(new Date(y, m - 1, d));
  }

  private payload(): ContractConditionsPayload {
    const v = this.form.getRawValue();
    return {
      tipoContrato: v.tipoContrato,
      otrosConceptos: Number(v.otrosConceptos ?? 0),
      descuento: Number(v.descuento ?? 0),
      cuotaInicial: Number(v.cuotaInicial ?? 0),
      numeroCuotas: Number(v.numeroCuotas),
      fechaInicio: v.fechaInicio,
      fechaFinalizacion: null,
    };
  }
}
