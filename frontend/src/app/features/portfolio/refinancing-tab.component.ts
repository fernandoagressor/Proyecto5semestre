import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { PortfolioContract, Refinancing } from '../../core/models/portfolio.models';
import { AuthService } from '../../core/services/auth.service';
import { PortfolioService } from '../../core/services/portfolio.service';
import { TeamService } from '../../core/services/team.service';
import { apiErrorMessage, NX_UI, todayIso, ToastService } from '../../shared/ui';

/** Refinanciaciones: propuestas sobre el saldo de un contrato, su aprobación y su aplicación al plan. */
@Component({
  selector: 'app-refinancing-tab',
  imports: [...NX_UI, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './portfolio.sections.scss',
  template: `
    <div class="nx-section-head">
      <div><h2 class="nx-section-title">Refinanciaciones</h2><p class="nx-section-subtitle">Un nuevo plan sobre el saldo pendiente. Se propone, se aprueba y se aplica.</p></div>
      @if (canPropose) { <button type="button" class="nx-btn nx-btn--sm nx-btn--primary" (click)="openForm()"><app-icon name="plus" /> Proponer</button> }
    </div>
    <nx-state [loading]="loading()" [error]="error()" [empty]="items().length === 0" (retry)="load()" emptyIcon="refresh" emptyTitle="Sin refinanciaciones">
      <div class="nx-table-wrap">
        <table class="nx-table">
          <thead><tr><th>Código</th><th>Contrato</th><th class="nx-right">Saldo</th><th>Plan nuevo</th><th>Motivo</th><th>Solicitó</th><th>Estado</th><th class="nx-actions"></th></tr></thead>
          <tbody>
            @for (r of items(); track r.id) {
              <tr>
                <td class="nx-mono"><b>{{ r.codigo }}</b></td>
                <td><b>{{ contractLabel(r.contratoId) }}</b><div class="sub">{{ clientName(r.contratoId) }}</div></td>
                <td class="nx-right nx-num">{{ r.saldoRefinanciado | cop }}</td>
                <td>{{ r.numeroCuotas }} cuotas · {{ r.tasaNueva * 100 | porcentaje: 2 }} EA<div class="sub">Desde {{ r.fechaPrimeraCuota | fecha }}</div></td>
                <td class="motive">{{ r.motivo }}</td>
                <td>{{ team.name(r.solicitadoPor) }}</td>
                <td><nx-badge [status]="r.estado" /></td>
                <td class="nx-actions">
                  @if (canApprove && (r.estado === 'SOLICITADA' || r.estado === 'EN_ESTUDIO')) {
                    <button type="button" class="nx-btn nx-btn--xs nx-btn--primary" (click)="act(r, 'aprobar')" [disabled]="busy()">Aprobar</button>
                    <button type="button" class="nx-btn nx-btn--xs nx-btn--ghost" (click)="act(r, 'rechazar')" [disabled]="busy()">Rechazar</button>
                  }
                  @if (canApprove && r.estado === 'APROBADA') {
                    <button type="button" class="nx-btn nx-btn--xs nx-btn--primary" (click)="act(r, 'aplicar')" [disabled]="busy()">Aplicar al plan</button>
                  }
                  <button type="button" class="nx-btn nx-btn--xs" (click)="detail.set(r)">Cuotas</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </nx-state>

    <nx-drawer [open]="formOpen()" (openChange)="formOpen.set($event)" title="Proponer refinanciación" subtitle="Se calcula sobre el saldo pendiente del contrato." [dismissible]="!busy()">
      <div class="nx-stack">
        <label class="nx-field">
          <span>Contrato</span>
          <select class="nx-select" [(ngModel)]="contractId">
            <option [ngValue]="null">Seleccione…</option>
            @for (c of withBalance(); track c.contratoId) { <option [ngValue]="c.contratoId">{{ c.contratoCodigo }} · {{ c.clienteNombre }} · saldo {{ c.saldoPendiente | cop: 'compact' }}</option> }
          </select>
        </label>
        <div class="nx-grid nx-grid--2">
          <label class="nx-field"><span>Número de cuotas</span><input class="nx-input" type="number" min="1" max="600" [(ngModel)]="installments" /></label>
          <label class="nx-field"><span>Interés efectivo anual (%)</span><input class="nx-input" type="number" min="0" max="100" step="0.1" [(ngModel)]="rate" /></label>
        </div>
        <label class="nx-field"><span>Primera cuota</span><input class="nx-input" type="date" [min]="today" [(ngModel)]="firstDate" /></label>
        <label class="nx-field"><span>Motivo</span><textarea class="nx-textarea" maxlength="2000" [(ngModel)]="reason" placeholder="Cliente perdió el empleo; propone ampliar el plazo"></textarea></label>
      </div>
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="formOpen.set(false)" [disabled]="busy()">Cancelar</button>
        <button type="button" class="nx-btn nx-btn--primary" (click)="propose()" [disabled]="busy() || !contractId || !reason.trim() || installments < 1">Proponer</button>
      </ng-container>
    </nx-drawer>

    <nx-drawer [open]="!!detail()" (openChange)="$event || detail.set(null)" [title]="detail()?.codigo ?? ''" [subtitle]="detail() ? contractLabel(detail()!.contratoId) + ' · ' + detail()!.numeroCuotas + ' cuotas' : ''">
      @if (detail(); as r) {
        <div class="nx-table-wrap">
          <table class="nx-table nx-table--compact">
            <thead><tr><th>#</th><th>Fecha</th><th class="nx-right">Capital</th><th class="nx-right">Interés</th><th class="nx-right">Cuota</th></tr></thead>
            <tbody>
              @for (c of r.cuotas; track c.numero) {
                <tr><td>{{ c.numero }}</td><td>{{ c.fecha | fecha }}</td><td class="nx-right nx-num">{{ c.capital | cop }}</td><td class="nx-right nx-num">{{ c.interes | cop }}</td><td class="nx-right nx-num"><b>{{ c.total | cop }}</b></td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    </nx-drawer>
  `,
})
export class RefinancingTabComponent {
  private readonly api = inject(PortfolioService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  readonly team = inject(TeamService);

  readonly contracts = input<PortfolioContract[]>([]);

  readonly canPropose = this.auth.hasAuthority('REFINANCIACIONES_PROPONER');
  readonly canApprove = this.auth.hasAuthority('REFINANCIACIONES_APROBAR');
  readonly items = signal<Refinancing[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly formOpen = signal(false);
  readonly detail = signal<Refinancing | null>(null);
  readonly withBalance = computed(() => this.contracts().filter((c) => c.planPagoId && c.saldoPendiente > 0));
  readonly today = todayIso();

  contractId: number | null = null;
  installments = 24;
  rate = 12;
  firstDate = todayIso(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 5));
  reason = '';

  constructor() {
    this.team.load();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.refinancings().subscribe({
      next: (list) => {
        this.items.set([...list].sort((a, b) => b.id - a.id));
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudieron cargar las refinanciaciones.'));
        this.loading.set(false);
      },
    });
  }

  contractLabel(id: number): string {
    return this.contracts().find((c) => c.contratoId === id)?.contratoCodigo ?? `Contrato #${id}`;
  }

  clientName(id: number): string {
    return this.contracts().find((c) => c.contratoId === id)?.clienteNombre ?? '';
  }

  openForm(): void {
    this.contractId = null;
    this.reason = '';
    this.formOpen.set(true);
  }

  propose(): void {
    if (!this.contractId) return;
    this.busy.set(true);
    this.api
      .proposeRefinancing({
        contratoId: this.contractId,
        numeroCuotas: Number(this.installments),
        tasaEfectivaAnual: Number((Number(this.rate) / 100).toFixed(6)),
        fechaPrimeraCuota: this.firstDate,
        motivo: this.reason.trim(),
      })
      .subscribe({
        next: (r) => this.done(`Refinanciación ${r.codigo} propuesta.`, () => this.formOpen.set(false)),
        error: (e) => this.failed(e),
      });
  }

  act(r: Refinancing, action: 'aprobar' | 'rechazar' | 'aplicar'): void {
    const messages = { aprobar: 'aprobada', rechazar: 'rechazada', aplicar: 'aplicada: el contrato tiene un plan nuevo' };
    this.busy.set(true);
    this.api.refinancingAction(r.id, action).subscribe({
      next: () => this.done(`Refinanciación ${r.codigo} ${messages[action]}.`),
      error: (e) => this.failed(e),
    });
  }

  private done(message: string, after?: () => void): void {
    this.busy.set(false);
    this.toast.success(message);
    after?.();
    this.load();
  }

  private failed(e: unknown): void {
    this.busy.set(false);
    this.toast.error(apiErrorMessage(e));
  }
}
