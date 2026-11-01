import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { BankMovement, PortfolioPayment, Reconciliation } from '../../core/models/portfolio.models';
import { PortfolioService } from '../../core/services/portfolio.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, formatCop, NX_UI, todayIso, ToastService } from '../../shared/ui';

/**
 * Conciliación bancaria: se crea por cuenta y rango, se cargan los movimientos del extracto y se
 * cruzan con los pagos confirmados de NEXORA (sugiere los de igual valor) o se ignoran.
 */
@Component({
  selector: 'app-reconciliation-tab',
  imports: [...NX_UI, FormsModule, MoneyInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './portfolio.sections.scss',
  template: `
    <div class="nx-section-head">
      <div><h2 class="nx-section-title">Conciliación bancaria</h2><p class="nx-section-subtitle">Cruza los movimientos del extracto con los pagos confirmados.</p></div>
      <div class="nx-row">
        @if (list().length > 1) {
          <select class="nx-select nx-select--sm" [ngModel]="selectedId()" (ngModelChange)="selectedId.set($event)" aria-label="Conciliación">
            @for (c of list(); track c.id) { <option [ngValue]="c.id">{{ c.codigo }} · {{ c.cuentaBancaria }} · {{ c.fechaDesde | fecha }} – {{ c.fechaHasta | fecha }}</option> }
          </select>
        }
        <button type="button" class="nx-btn nx-btn--sm" (click)="newOpen.set(true)"><app-icon name="plus" /> Nueva</button>
      </div>
    </div>

    <nx-state [loading]="loading()" [error]="error()" [empty]="!current()" (retry)="load()" emptyIcon="wallet" emptyTitle="Sin conciliaciones" emptyText="Cree una por cuenta y periodo para cargar el extracto.">
      @if (current(); as c) {
        <div class="recon-head">
          <span><b>{{ c.cuentaBancaria }}</b> · {{ c.fechaDesde | fecha }} – {{ c.fechaHasta | fecha }} · <nx-badge [status]="c.estado" /></span>
          <span><b>{{ counts().ok }}</b> conciliados · <b>{{ counts().pending }}</b> pendientes · saldo extracto {{ c.saldoExtracto | cop }} · conciliado {{ c.totalConciliado | cop }}</span>
        </div>
        <div class="nx-table-wrap">
          <table class="nx-table">
            <thead><tr><th>Fecha</th><th>Movimiento del banco</th><th class="nx-right">Valor</th><th>Pago en NEXORA</th><th>Estado</th><th class="nx-actions"></th></tr></thead>
            <tbody>
              @for (m of c.movimientos; track m.id) {
                <tr>
                  <td class="nx-nowrap">{{ m.fecha | fecha }}</td>
                  <td><b>{{ m.referencia }}</b>@if (m.descripcion) { <div class="sub">{{ m.descripcion }}</div> }</td>
                  <td class="nx-right nx-num"><b>{{ m.valor | cop }}</b></td>
                  <td>
                    @if (m.pagoId) {
                      {{ paymentLabel(m.pagoId) }}
                      @if (m.diferencia) { <div class="sub warn">Diferencia {{ m.diferencia | cop }}</div> }
                    } @else if (open() && m.estado === 'PENDIENTE') {
                      <select class="nx-select nx-select--sm" [ngModel]="choice()[m.id] ?? null" (ngModelChange)="choose(m.id, $event)" [attr.aria-label]="'Pago para ' + m.referencia">
                        <option [ngValue]="null">Elegir pago…</option>
                        @for (p of candidates(m); track p.id) { <option [ngValue]="p.id">{{ p.codigo }} · {{ money(p.valor) }} · {{ p.fechaPago | fecha }}{{ p.valor === m.valor ? ' · mismo valor' : '' }}</option> }
                      </select>
                    } @else { <span class="nx-subtle">—</span> }
                  </td>
                  <td><nx-badge [status]="m.estado" /></td>
                  <td class="nx-actions">
                    @if (open() && m.estado === 'PENDIENTE') {
                      <button type="button" class="nx-btn nx-btn--xs nx-btn--primary" (click)="match(m)" [disabled]="busy() || !choice()[m.id]">Conciliar</button>
                      <button type="button" class="nx-btn nx-btn--xs nx-btn--ghost" (click)="ignore(m)" [disabled]="busy()">Ignorar</button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="nx-subtle">Sin movimientos. Agregue los del extracto.</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if (open()) {
          <div class="recon-add">
            <input class="nx-input nx-input--sm" type="date" [(ngModel)]="mDate" aria-label="Fecha del movimiento" />
            <input class="nx-input nx-input--sm" [(ngModel)]="mRef" placeholder="Referencia del banco" aria-label="Referencia" />
            <input class="nx-input nx-input--sm" [(ngModel)]="mDesc" placeholder="Descripción (opcional)" aria-label="Descripción" />
            <input class="nx-input nx-input--sm" nexoraMoney [(ngModel)]="mValue" placeholder="Valor" aria-label="Valor" />
            <button type="button" class="nx-btn nx-btn--sm" (click)="addMovement()" [disabled]="busy() || !mRef.trim() || !mValue">Agregar</button>
          </div>
          <div class="nx-row nx-row--end">
            <button type="button" class="nx-btn nx-btn--sm" (click)="close()" [disabled]="busy() || counts().pending > 0" [title]="counts().pending > 0 ? 'Concilie o ignore los pendientes antes de cerrar' : ''">Cerrar conciliación</button>
          </div>
        }
      }
    </nx-state>

    <nx-drawer [open]="newOpen()" (openChange)="newOpen.set($event)" variant="dialog" title="Nueva conciliación" [dismissible]="!busy()">
      <div class="nx-stack">
        <label class="nx-field"><span>Cuenta bancaria</span><input class="nx-input" [(ngModel)]="account" placeholder="Bancolombia ahorros ****4821" /></label>
        <div class="nx-grid nx-grid--2">
          <label class="nx-field"><span>Desde</span><input class="nx-input" type="date" [(ngModel)]="from" /></label>
          <label class="nx-field"><span>Hasta</span><input class="nx-input" type="date" [(ngModel)]="to" /></label>
        </div>
        <label class="nx-field"><span>Saldo del extracto</span><input class="nx-input" nexoraMoney [(ngModel)]="balance" /></label>
      </div>
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="newOpen.set(false)" [disabled]="busy()">Cancelar</button>
        <button type="button" class="nx-btn nx-btn--primary" (click)="create()" [disabled]="busy() || !account.trim() || !from || !to">Crear</button>
      </ng-container>
    </nx-drawer>
  `,
})
export class ReconciliationTabComponent {
  private readonly api = inject(PortfolioService);
  private readonly toast = inject(ToastService);

  /** Pagos confirmados que la página ya cargó, para sugerir el cruce. */
  readonly payments = input<PortfolioPayment[]>([]);

  readonly list = signal<Reconciliation[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly newOpen = signal(false);
  readonly choice = signal<Record<number, number | null>>({});

  readonly current = computed(() => this.list().find((c) => c.id === this.selectedId()) ?? this.list()[0] ?? null);
  readonly open = computed(() => {
    const c = this.current();
    return !!c && (c.estado === 'BORRADOR' || c.estado === 'EN_PROCESO');
  });
  readonly counts = computed(() => {
    const m = this.current()?.movimientos ?? [];
    return { ok: m.filter((x) => x.estado === 'CONCILIADO' || x.estado === 'DIFERENCIA').length, pending: m.filter((x) => x.estado === 'PENDIENTE').length };
  });
  private readonly used = computed(() => new Set(this.list().flatMap((c) => c.movimientos.map((m) => m.pagoId)).filter((x): x is number => !!x)));

  account = '';
  from = todayIso(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  to = todayIso();
  balance: number | null = null;
  mDate = todayIso();
  mRef = '';
  mDesc = '';
  mValue: number | null = null;

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.reconciliations().subscribe({
      next: (list) => {
        const sorted = [...list].sort((a, b) => b.id - a.id);
        this.list.set(sorted);
        if (!sorted.some((c) => c.id === this.selectedId())) this.selectedId.set(sorted[0]?.id ?? null);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudo cargar la conciliación.'));
        this.loading.set(false);
      },
    });
  }

  /** Pagos confirmados aún no cruzados, primero los de igual valor. */
  candidates(m: BankMovement): PortfolioPayment[] {
    const used = this.used();
    return this.payments()
      .filter((p) => p.estado === 'CONFIRMADO' && !used.has(p.id))
      .sort((a, b) => Number(b.valor === m.valor) - Number(a.valor === m.valor) || Math.abs(a.valor - m.valor) - Math.abs(b.valor - m.valor))
      .slice(0, 30);
  }

  paymentLabel(id: number): string {
    const p = this.payments().find((x) => x.id === id);
    return p ? `${p.codigo} · ${formatCop(p.valor)}` : `Pago #${id}`;
  }

  money(v: number): string {
    return formatCop(v);
  }

  choose(movementId: number, paymentId: number | null): void {
    this.choice.update((c) => ({ ...c, [movementId]: paymentId }));
  }

  create(): void {
    this.run(
      this.api.createReconciliation({ cuentaBancaria: this.account.trim(), fechaDesde: this.from, fechaHasta: this.to, saldoExtracto: Number(this.balance ?? 0) }),
      'Conciliación creada. Agregue los movimientos del extracto.',
      (c) => {
        this.newOpen.set(false);
        this.selectedId.set((c as Reconciliation).id);
      },
    );
  }

  addMovement(): void {
    const c = this.current();
    if (!c) return;
    this.run(
      this.api.addMovement(c.id, { fechaMovimiento: this.mDate, referencia: this.mRef.trim(), descripcion: this.mDesc.trim() || null, valor: Number(this.mValue) }),
      'Movimiento agregado.',
      () => {
        this.mRef = '';
        this.mDesc = '';
        this.mValue = null;
      },
    );
  }

  match(m: BankMovement): void {
    const c = this.current();
    const pagoId = this.choice()[m.id];
    if (c && pagoId) this.run(this.api.matchMovement(c.id, m.id, pagoId), 'Movimiento conciliado.');
  }

  ignore(m: BankMovement): void {
    const c = this.current();
    if (c) this.run(this.api.ignoreMovement(c.id, m.id), 'Movimiento ignorado.');
  }

  close(): void {
    const c = this.current();
    if (c) this.run(this.api.closeReconciliation(c.id), `Conciliación ${c.codigo} cerrada.`);
  }

  private run(request: import('rxjs').Observable<unknown>, message: string, after?: (value: unknown) => void): void {
    this.busy.set(true);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        this.toast.success(message);
        after?.(value);
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }
}
