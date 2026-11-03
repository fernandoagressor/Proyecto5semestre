import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, finalize, of, switchMap } from 'rxjs';

import { Contract } from '../../core/models/sales.models';
import { AuthService } from '../../core/services/auth.service';
import { DownloadService } from '../../core/services/download.service';
import { Withdrawal, WithdrawalService, WithdrawalSettlement } from '../../core/services/withdrawal.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, ChipOption, NX_UI, ToastService } from '../../shared/ui';

const FILTERS: ChipOption[] = [
  { value: '', label: 'Todos' },
  { value: 'SOLICITADO', label: 'Por aprobar' },
  { value: 'APROBADO', label: 'Aprobados' },
  { value: 'RECHAZADO', label: 'Rechazados' },
];

/**
 * Desistimientos (vista "Ventas-Desistimiento"): liquidación en vivo, solicitud, aprobación con
 * separación de funciones y acta en PDF. Aprobar anula el contrato y libera el lote.
 */
@Component({
  selector: 'app-withdrawals-tab',
  imports: [...NX_UI, ReactiveFormsModule, MoneyInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="nx-section-head">
      <div><h2 class="nx-section-title">Desistimientos</h2><p class="nx-section-subtitle">Liquidación con cláusula penal, aprobación y acta. Al aprobar, el lote vuelve a estar disponible.</p></div>
      <div class="nx-row">
        <nx-filter-chips [options]="filters" [value]="status()" (valueChange)="status.set($event); load()" label="Estado" />
        @if (canRequest) { <button type="button" class="nx-btn nx-btn--primary" (click)="openNew()"><app-icon name="plus" /> Registrar desistimiento</button> }
      </div>
    </div>
    <nx-state [loading]="loading()" [error]="error()" [empty]="items().length === 0" (retry)="load()" emptyIcon="file"
      emptyTitle="Sin desistimientos" emptyText="Cuando un cliente desista de su compra, regístralo aquí para liquidarlo.">
      <div class="nx-table-wrap">
        <table class="nx-table">
          <thead><tr><th>Desistimiento</th><th>Contrato / cliente</th><th>Proyecto · lote</th><th class="nx-right">Pagado</th><th class="nx-right">A devolver</th><th>Estado</th><th class="nx-actions"></th></tr></thead>
          <tbody>
            @for (d of items(); track d.id) {
              <tr>
                <td><b class="nx-mono">{{ d.codigo }}</b><div class="sub">{{ d.creadoEn | fecha }} · {{ d.origen === 'CLIENTE' ? 'Pedido del cliente' : 'Decisión de la empresa' }}</div></td>
                <td><b>{{ d.contrato }}</b><div class="sub">{{ d.cliente }}</div></td>
                <td>{{ d.proyecto }}<div class="sub">Lote {{ d.lote }}</div></td>
                <td class="nx-right nx-num">{{ d.totalPagado | cop }}</td>
                <td class="nx-right nx-num"><b>{{ d.valorDevolver | cop }}</b><div class="sub">Penal {{ d.porcentajePenalidad }} %</div></td>
                <td><nx-badge [status]="d.estado" [text]="statusLabel(d.estado)" /></td>
                <td class="nx-actions">
                  <div class="row-actions">
                    <button type="button" class="nx-btn nx-btn--xs" (click)="act(d)"><app-icon name="download" /> Acta</button>
                    @if (canApprove && d.estado === 'SOLICITADO') { <button type="button" class="nx-btn nx-btn--xs nx-btn--primary" (click)="review.set(d); reviewComment.setValue('')">Revisar</button> }
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </nx-state>

    <nx-drawer [open]="formOpen()" (openChange)="formOpen.set($event)" width="640px" title="Registrar desistimiento" subtitle="Queda pendiente de aprobación; nada cambia hasta entonces." [dismissible]="!saving()">
      <form class="nx-stack" [formGroup]="form" (ngSubmit)="submit()" id="withdrawal-form">
        <label class="nx-field"><span>Contrato</span>
          <select class="nx-select" formControlName="contratoId">
            <option [value]="0">Selecciona…</option>
            @for (c of eligible(); track c.id) { <option [value]="c.id">{{ c.codigo }} · {{ nameOf(c.clientePrincipalId) }} · {{ c.estado === 'VIGENTE' ? 'vigente' : 'por firmar' }}</option> }
          </select>
        </label>
        <div class="nx-form-grid">
          <label class="nx-field"><span>Lo pide</span>
            <select class="nx-select" formControlName="origen"><option value="CLIENTE">El cliente</option><option value="EMPRESA">La empresa</option></select>
          </label>
          <label class="nx-field"><span>Cláusula penal (%)</span><input class="nx-input" type="number" min="0" max="100" step="0.5" formControlName="porcentajePenalidad" /></label>
          <label class="nx-field"><span>Otros descuentos</span><input class="nx-input" type="text" nexoraMoney formControlName="otrosDescuentos" /><span class="nx-field__hint">Gastos de escrituración, comisiones pagadas, etc.</span></label>
          <label class="nx-field"><span>Devolver a más tardar</span><input class="nx-input" type="date" formControlName="fechaDevolucion" /></label>
          <label class="nx-field nx-span-all"><span>Motivo</span><textarea class="nx-textarea" rows="3" formControlName="motivo" placeholder="Ej.: el cliente se traslada de ciudad"></textarea></label>
          <label class="nx-field nx-span-all"><span>Observaciones (opcional)</span><textarea class="nx-textarea" rows="2" formControlName="observaciones"></textarea></label>
        </div>

        @if (settlement(); as s) {
          <section class="liq" aria-label="Liquidación">
            <h3>Liquidación</h3>
            <dl>
              <div><dt>Valor del contrato</dt><dd>{{ s.valorContrato | cop }}</dd></div>
              <div><dt>Pagado por el cliente</dt><dd>{{ s.totalPagado | cop }}</dd></div>
              <div><dt>Cláusula penal ({{ s.porcentajePenalidad }} %)</dt><dd>− {{ s.valorPenalidad | cop }}</dd></div>
              <div><dt>Otros descuentos</dt><dd>− {{ s.otrosDescuentos | cop }}</dd></div>
              <div class="total"><dt>A devolver</dt><dd>{{ s.valorDevolver | cop }}</dd></div>
            </dl>
            <ul class="effects">
              <li>Se cancelarán <b>{{ s.cuotasAbiertas }}</b> cuotas pendientes y el lote <b>{{ s.lote }}</b> quedará disponible.</li>
              <li>Se anularán <b>{{ s.comisionesPendientes }}</b> comisiones no pagadas.</li>
              @if (s.comisionesPagadas > 0) { <li class="warn">Hay {{ s.comisionesPagadas }} comisiones ya pagadas: considéralas en "otros descuentos" o recupéralas con el asesor.</li> }
            </ul>
          </section>
        } @else if (settlementLoading()) {
          <nx-state [loading]="true" />
        }
      </form>
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="formOpen.set(false)" [disabled]="saving()">Cancelar</button>
        <button type="submit" form="withdrawal-form" class="nx-btn nx-btn--primary" [disabled]="saving() || !settlement()">{{ saving() ? 'Registrando…' : 'Registrar para aprobación' }}</button>
      </ng-container>
    </nx-drawer>

    <nx-drawer [open]="!!review()" (openChange)="$event || review.set(null)" variant="dialog" title="Revisar desistimiento" [subtitle]="review()?.codigo ?? ''" [dismissible]="!saving()">
      @if (review(); as d) {
        <div class="nx-stack">
          <div class="nx-alert nx-alert--warning"><app-icon name="warning" /><span>Aprobar anula el contrato <b>{{ d.contrato }}</b>, cancela sus cuotas pendientes, anula comisiones no pagadas y libera el lote <b>{{ d.lote }}</b>. Se devolverán <b>{{ d.valorDevolver | cop }}</b>.</span></div>
          <p class="nx-muted">Motivo: {{ d.motivo }}</p>
          <label class="nx-field"><span>Comentario {{ '(obligatorio para rechazar)' }}</span><textarea class="nx-textarea" rows="3" [formControl]="reviewComment"></textarea></label>
        </div>
      }
      <ng-container drawer-footer>
        <button type="button" class="nx-btn nx-btn--danger" [disabled]="saving() || !reviewComment.value.trim()" (click)="decide(false)">Rechazar</button>
        <button type="button" class="nx-btn nx-btn--primary" [disabled]="saving()" (click)="decide(true)">Aprobar desistimiento</button>
      </ng-container>
    </nx-drawer>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 14px; }
    .sub { margin-top: 2px; color: var(--text-muted); font-size: 12px; }
    .row-actions { display: flex; flex-wrap: nowrap; justify-content: flex-end; gap: 6px; }
    .liq { padding: 16px; border: 1px solid var(--border-soft); border-radius: 14px; background: var(--surface-muted); }
    .liq h3 { margin: 0 0 10px; font-size: 15px; }
    .liq dl { display: flex; flex-direction: column; gap: 6px; margin: 0; }
    .liq dl > div { display: flex; justify-content: space-between; gap: 12px; font-size: 14px; }
    .liq dt { color: var(--text-muted); }
    .liq dd { margin: 0; font-weight: 600; font-variant-numeric: tabular-nums; }
    .liq .total { margin-top: 4px; padding-top: 8px; border-top: 1px solid var(--border-soft); font-size: 16px; }
    .liq .total dd { color: var(--color-primary-strong); font-weight: 800; }
    .effects { margin: 12px 0 0; padding-left: 18px; color: var(--text-muted); font-size: 13px; line-height: 1.5; }
    .effects .warn { color: var(--tone-warning-fg); }
  `,
})
export class WithdrawalsTabComponent {
  private readonly api = inject(WithdrawalService);
  private readonly downloads = inject(DownloadService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);

  readonly contracts = input<Contract[]>([]);
  readonly customerNames = input<Record<number, string>>({});
  readonly changed = output<void>();

  readonly canRequest = this.auth.hasAuthority('DESISTIMIENTOS_SOLICITAR');
  readonly canApprove = this.auth.hasAuthority('DESISTIMIENTOS_APROBAR');
  readonly filters = FILTERS;
  readonly status = signal('');
  readonly items = signal<Withdrawal[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly formOpen = signal(false);
  readonly settlement = signal<WithdrawalSettlement | null>(null);
  readonly settlementLoading = signal(false);
  readonly review = signal<Withdrawal | null>(null);
  readonly eligible = computed(() => this.contracts().filter((c) => ['VIGENTE', 'PENDIENTE_FIRMA', 'NEGOCIACION'].includes(c.estado)));

  readonly form = this.fb.group({
    contratoId: this.fb.control(0, Validators.min(1)),
    origen: this.fb.control<'CLIENTE' | 'EMPRESA'>('CLIENTE'),
    porcentajePenalidad: this.fb.control(10, [Validators.required, Validators.min(0), Validators.max(100)]),
    otrosDescuentos: this.fb.control(0, Validators.min(0)),
    fechaDevolucion: this.fb.control(''),
    motivo: this.fb.control('', [Validators.required, Validators.maxLength(1000)]),
    observaciones: this.fb.control('', Validators.maxLength(2000)),
  });
  readonly reviewComment = this.fb.control('', Validators.maxLength(1000));

  constructor() {
    this.load();
    this.form.valueChanges
      .pipe(
        debounceTime(350),
        switchMap((v) => {
          const id = Number(v.contratoId);
          if (!id || this.form.controls.porcentajePenalidad.invalid) {
            this.settlement.set(null);
            return of(null);
          }
          this.settlementLoading.set(true);
          return this.api.settlement(id, Number(v.porcentajePenalidad ?? 0), Number(v.otrosDescuentos ?? 0)).pipe(
            catchError((e) => {
              this.toast.error(apiErrorMessage(e, 'No se pudo calcular la liquidación.'));
              return of(null);
            }),
            finalize(() => this.settlementLoading.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((s) => {
        if (s !== null || !Number(this.form.controls.contratoId.value)) this.settlement.set(s);
      });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list(this.status())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({ next: (list) => this.items.set(list), error: (e) => this.error.set(apiErrorMessage(e, 'No se pudieron cargar los desistimientos.')) });
  }

  openNew(): void {
    this.form.reset({ contratoId: 0, origen: 'CLIENTE', porcentajePenalidad: 10, otrosDescuentos: 0, fechaDevolucion: '', motivo: '', observaciones: '' });
    this.settlement.set(null);
    this.formOpen.set(true);
  }

  submit(): void {
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .request({
        contratoId: Number(v.contratoId),
        origen: v.origen,
        motivo: v.motivo.trim(),
        porcentajePenalidad: Number(v.porcentajePenalidad),
        otrosDescuentos: Number(v.otrosDescuentos) || 0,
        fechaDevolucion: v.fechaDevolucion || null,
        observaciones: v.observaciones.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (d) => {
          this.formOpen.set(false);
          this.toast.success(`Desistimiento ${d.codigo} registrado; queda pendiente de aprobación.`);
          this.load();
        },
        error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo registrar el desistimiento.')),
      });
  }

  decide(approve: boolean): void {
    const d = this.review();
    if (!d) return;
    const comment = this.reviewComment.value.trim();
    this.saving.set(true);
    (approve ? this.api.approve(d, comment || null) : this.api.reject(d, comment))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.review.set(null);
          this.toast.success(approve ? 'Desistimiento aprobado: contrato anulado y lote disponible.' : 'Desistimiento rechazado.');
          this.load();
          if (approve) this.changed.emit();
        },
        error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo completar la revisión.')),
      });
  }

  act(d: Withdrawal): void {
    this.downloads.withdrawalAct(d.id).subscribe({ error: () => this.toast.error('No se pudo generar el acta.') });
  }

  nameOf(id: number): string {
    return this.customerNames()[id] ?? `Cliente #${id}`;
  }

  statusLabel(status: string): string {
    return status === 'SOLICITADO' ? 'Por aprobar' : status === 'APROBADO' ? 'Aprobado' : 'Rechazado';
  }
}
