import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';

import { Appointment, AppointmentStatus } from '../../core/models/crm.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { TeamService } from '../../core/services/team.service';
import { apiErrorMessage, ChipOption, localDateTimeIso, NX_UI, ToastService } from '../../shared/ui';
import { CrmNames } from './crm-names.service';
import { fromLocalInput, toLocalInput } from './crm-labels';

type Dialog = 'realizada' | 'reprogramar' | null;

/** Agenda comercial: citas de los próximos días agrupadas por fecha, con sus cambios de estado. */
@Component({
  selector: 'app-agenda-tab',
  imports: [...NX_UI],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './crm.shared.scss',
  template: `
    <section class="nx-card nx-card--pad nx-card--stack" aria-label="Agenda comercial">
      <div class="nx-section-head">
        <h2 class="nx-section-title">{{ compact() ? 'Agenda comercial · próximos 7 días' : 'Agenda comercial' }}</h2>
        @if (!compact()) {
          <nx-filter-chips [options]="rangeOptions" [value]="days()" (valueChange)="days.set($event)" label="Rango de la agenda" />
        }
      </div>
      <nx-state [loading]="loading()" [error]="error()" [empty]="items().length === 0" (retry)="load()"
        emptyIcon="calendar" emptyTitle="Agenda despejada" emptyText="No hay citas programadas en este rango.">
        @if (compact()) {
          <div class="agenda-strip">
            @for (a of items().slice(0, 6); track a.id) {
              <button type="button" class="agenda-chip" (click)="openOpportunity.emit(a.oportunidadId)">
                <span>{{ dayLabel(a.fechaInicio) }} · {{ time(a.fechaInicio) }}</span>
                <b>{{ typeLabel(a.tipo) }} · {{ names.opportunityTitle(a.oportunidadId) }}</b>
                <small>{{ a.lugar || team.name(a.asesorId) }}</small>
              </button>
            }
          </div>
        } @else {
          @for (group of groups(); track group.day) {
            <h3 class="agenda-day">{{ group.label }}</h3>
            <div class="nx-list">
              @for (a of group.items; track a.id) {
                <div class="agenda-row">
                  <span class="nx-mono">{{ time(a.fechaInicio) }}–{{ time(a.fechaFin) }}</span>
                  <button type="button" class="nx-btn--link agenda-row__who" (click)="openOpportunity.emit(a.oportunidadId)">
                    <b>{{ typeLabel(a.tipo) }} · {{ names.opportunityTitle(a.oportunidadId) }}</b>
                    <small>{{ a.lugar || 'Sin lugar' }} · {{ team.name(a.asesorId) }}</small>
                  </button>
                  <nx-badge [status]="a.estado" />
                  @if (canManage && (a.estado === 'PROGRAMADA' || a.estado === 'CONFIRMADA' || a.estado === 'REPROGRAMADA')) {
                    <div class="row-actions">
                      @if (a.estado !== 'CONFIRMADA') {
                        <button type="button" class="nx-btn nx-btn--xs" (click)="change(a, 'CONFIRMADA')" [disabled]="busy()">Confirmar</button>
                      }
                      <button type="button" class="nx-btn nx-btn--xs nx-btn--primary" (click)="ask(a, 'realizada')" [disabled]="busy()">Realizada</button>
                      <button type="button" class="nx-btn nx-btn--xs" (click)="ask(a, 'reprogramar')" [disabled]="busy()">Reprogramar</button>
                      <button type="button" class="nx-btn nx-btn--xs nx-btn--ghost" (click)="change(a, 'NO_ASISTIO')" [disabled]="busy()">No asistió</button>
                      <button type="button" class="nx-btn nx-btn--xs nx-btn--ghost" (click)="change(a, 'CANCELADA')" [disabled]="busy()">Cancelar</button>
                    </div>
                  }
                </div>
              }
            </div>
          }
        }
      </nx-state>
    </section>

    <nx-drawer [(open)]="dialogOpen" variant="dialog" [title]="dialog() === 'realizada' ? 'Cita realizada' : 'Reprogramar cita'"
      [subtitle]="target() ? names.opportunityTitle(target()!.oportunidadId) : ''" [dismissible]="!busy()">
      @if (dialog() === 'realizada') {
        <label class="nx-field"><span>Resultado</span><textarea class="nx-textarea" [value]="result()" (input)="result.set($any($event.target).value)" placeholder="Visitó el lote M03-L12; pide simulación a 36 cuotas"></textarea></label>
      } @else {
        <div class="nx-grid nx-grid--2">
          <label class="nx-field"><span>Nuevo inicio</span><input class="nx-input" type="datetime-local" [value]="start()" (change)="start.set($any($event.target).value)" /></label>
          <label class="nx-field">
            <span>Duración</span>
            <select class="nx-select" [value]="duration()" (change)="duration.set(+$any($event.target).value)">
              <option value="30">30 min</option><option value="60">1 hora</option><option value="90">1 h 30</option><option value="120">2 horas</option>
            </select>
          </label>
        </div>
      }
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="dialogOpen.set(false)" [disabled]="busy()">Cancelar</button>
        <button type="button" class="nx-btn nx-btn--primary" (click)="confirm()" [disabled]="busy() || (dialog() === 'reprogramar' && !start())">Guardar</button>
      </ng-container>
    </nx-drawer>
  `,
})
export class AgendaTabComponent {
  private readonly crm = inject(CrmService);
  private readonly toast = inject(ToastService);
  readonly names = inject(CrmNames);
  readonly team = inject(TeamService);

  /** Versión resumida (franja bajo el embudo) o completa (pestaña Agenda). */
  readonly compact = input(false);
  readonly reloadKey = input(0);
  readonly openOpportunity = output<number>();

  readonly canManage = inject(AuthService).hasAuthority('AGENDA_GESTIONAR');
  readonly rangeOptions: ChipOption[] = [
    { value: '7', label: '7 días' },
    { value: '14', label: '14 días' },
    { value: '30', label: '30 días' },
  ];
  readonly days = signal('7');
  readonly items = signal<Appointment[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly target = signal<Appointment | null>(null);
  readonly dialog = signal<Dialog>(null);
  readonly dialogOpen = signal(false);
  readonly result = signal('');
  readonly start = signal('');
  readonly duration = signal(60);

  readonly groups = computed(() => {
    const map = new Map<string, Appointment[]>();
    for (const a of this.items()) {
      const day = a.fechaInicio.slice(0, 10);
      map.set(day, [...(map.get(day) ?? []), a]);
    }
    return [...map.entries()].map(([day, items]) => ({ day, label: this.dayTitle(day), items }));
  });

  constructor() {
    effect(() => {
      const days = this.compact() ? 7 : Number(this.days());
      this.reloadKey();
      untracked(() => this.load(days));
    });
  }

  load(days = this.compact() ? 7 : Number(this.days())): void {
    this.loading.set(true);
    this.error.set(null);
    const from = new Date();
    from.setHours(0, 0, 0, 0);
    const to = new Date(from.getTime() + days * 86_400_000);
    this.crm.appointments({ from: localDateTimeIso(from), to: localDateTimeIso(to), size: 100 }).subscribe({
      next: (page) => {
        const list = [...page.contenido].sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));
        this.items.set(list);
        this.names.ensureOpportunities(list.map((a) => a.oportunidadId));
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudo cargar la agenda.'));
        this.loading.set(false);
      },
    });
  }

  time(value: string): string {
    return value.slice(11, 16);
  }

  dayLabel(value: string): string {
    return new Date(`${value.slice(0, 10)}T00:00:00`).toLocaleDateString('es-CO', { weekday: 'short', day: 'numeric', month: 'short' }).replace('.', '').toUpperCase();
  }

  dayTitle(day: string): string {
    const today = localDateTimeIso(new Date()).slice(0, 10);
    const tomorrow = localDateTimeIso(new Date(Date.now() + 86_400_000)).slice(0, 10);
    const label = new Date(`${day}T00:00:00`).toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long' });
    return day === today ? `Hoy · ${label}` : day === tomorrow ? `Mañana · ${label}` : label.charAt(0).toUpperCase() + label.slice(1);
  }

  typeLabel(type: string): string {
    return type.charAt(0) + type.slice(1).toLowerCase();
  }

  ask(a: Appointment, dialog: Dialog): void {
    this.target.set(a);
    this.dialog.set(dialog);
    this.result.set('');
    this.start.set(a.fechaInicio.slice(0, 16));
    this.duration.set(Math.max(15, Math.round((new Date(a.fechaFin).getTime() - new Date(a.fechaInicio).getTime()) / 60_000)) || 60);
    this.dialogOpen.set(true);
  }

  change(a: Appointment, estado: AppointmentStatus, resultado: string | null = null): void {
    this.busy.set(true);
    this.crm.changeAppointmentStatus(a.oportunidadId, a.id, estado, resultado).subscribe({
      next: () => this.done('Cita actualizada.'),
      error: (e) => this.failed(e),
    });
  }

  confirm(): void {
    const a = this.target();
    if (!a) return;
    if (this.dialog() === 'realizada') {
      this.change(a, 'REALIZADA', this.result().trim() || null);
      return;
    }
    const start = new Date(this.start());
    const end = new Date(start.getTime() + this.duration() * 60_000);
    this.busy.set(true);
    this.crm
      .rescheduleAppointment(a.oportunidadId, a.id, { tipo: a.tipo, fechaInicio: fromLocalInput(this.start())!, fechaFin: fromLocalInput(toLocalInput(end))!, lugar: a.lugar })
      .subscribe({ next: () => this.done('Cita reprogramada.'), error: (e) => this.failed(e) });
  }

  private done(message: string): void {
    this.busy.set(false);
    this.dialogOpen.set(false);
    this.toast.success(message);
    this.load();
  }

  private failed(e: unknown): void {
    this.busy.set(false);
    this.toast.error(apiErrorMessage(e));
  }
}
