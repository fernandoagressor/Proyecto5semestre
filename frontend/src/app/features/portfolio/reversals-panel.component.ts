import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';

import { PaymentReversal, PortfolioPayment } from '../../core/models/portfolio.models';
import { AuthService } from '../../core/services/auth.service';
import { PortfolioService } from '../../core/services/portfolio.service';
import { TeamService } from '../../core/services/team.service';
import { apiErrorMessage, NX_UI, ToastService } from '../../shared/ui';

/** Reversiones solicitadas: las aprueba o rechaza alguien distinto de quien las pidió. */
@Component({
  selector: 'app-reversals-panel',
  imports: [...NX_UI],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './portfolio.sections.scss',
  template: `
    @if (items().length > 0) {
      <div class="nx-alert nx-alert--warning reversals">
        <app-icon name="warning" />
        <div class="nx-stack" style="flex: 1; gap: 8px">
          <b>{{ items().length }} {{ items().length === 1 ? 'reversión espera' : 'reversiones esperan' }} aprobación</b>
          @for (r of items(); track r.id) {
            <div class="reversal-row">
              <span><b>{{ paymentCode(r.pagoId) }}</b> · {{ r.valor | cop }} · {{ r.motivo }} <span class="sub">· pidió {{ team.name(r.solicitadoPor) }}</span></span>
              @if (r.solicitadoPor !== myId) {
                <span class="nx-row">
                  <button type="button" class="nx-btn nx-btn--xs nx-btn--danger" (click)="act(r, 'aprobar')" [disabled]="busy()">Aprobar y reversar</button>
                  <button type="button" class="nx-btn nx-btn--xs" (click)="act(r, 'rechazar')" [disabled]="busy()">Rechazar</button>
                </span>
              } @else {
                <span class="sub">La aprueba otro usuario</span>
              }
            </div>
          }
        </div>
      </div>
    }
  `,
})
export class ReversalsPanelComponent {
  private readonly api = inject(PortfolioService);
  private readonly toast = inject(ToastService);
  readonly team = inject(TeamService);

  readonly payments = input<PortfolioPayment[]>([]);
  readonly reloadKey = input(0);
  readonly changed = output<void>();

  readonly myId = inject(AuthService).user()?.id ?? null;
  readonly items = signal<PaymentReversal[]>([]);
  readonly busy = signal(false);

  constructor() {
    this.team.load();
    effect(() => {
      this.reloadKey();
      untracked(() => this.load());
    });
  }

  load(): void {
    this.api.reversals('SOLICITADA').subscribe({ next: (list) => this.items.set(list), error: () => this.items.set([]) });
  }

  paymentCode(id: number): string {
    return this.payments().find((p) => p.id === id)?.codigo ?? `Pago #${id}`;
  }

  act(r: PaymentReversal, action: 'aprobar' | 'rechazar'): void {
    this.busy.set(true);
    this.api.reversalAction(r.pagoId, r.id, action).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(action === 'aprobar' ? 'Pago reversado: las cuotas recuperan su saldo.' : 'Reversión rechazada.');
        this.load();
        this.changed.emit();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }
}
