import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { ServicePriority, SupportService, SupportTicket, TicketMessage, TicketStatus } from '../../core/services/support.service';
import { TeamService } from '../../core/services/team.service';
import { apiErrorMessage, ChipOption, NX_UI, ToastService } from '../../shared/ui';

const STATUS_OPTIONS: ChipOption[] = [
  { value: 'ABIERTOS', label: 'Abiertos' },
  { value: 'RESUELTO', label: 'Resueltos' },
  { value: 'CERRADO', label: 'Cerrados' },
  { value: '', label: 'Todos' },
];

/** Tickets de soporte interno (problemas de la operación que atiende TI, jurídica o administración). */
@Component({
  selector: 'app-tickets-tab',
  imports: [...NX_UI, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './service.sections.scss',
  template: `
    <div class="nx-section-head">
      <div><h2 class="nx-section-title">Tickets de soporte interno</h2><p class="nx-section-subtitle">Incidencias de la operación para TI, jurídica o administración.</p></div>
      <div class="nx-row">
        <nx-filter-chips [options]="statusOptions" [value]="status()" (valueChange)="status.set($event)" label="Estado del ticket" />
        @if (canCreate) { <button type="button" class="nx-btn nx-btn--sm nx-btn--primary" (click)="openNew()"><app-icon name="plus" /> Nuevo ticket</button> }
      </div>
    </div>
    <nx-state [loading]="loading()" [error]="error()" [empty]="visible().length === 0" (retry)="load()" emptyIcon="ticket" emptyTitle="Sin tickets">
      <div class="nx-table-wrap">
        <table class="nx-table nx-table--clickable">
          <thead><tr><th>Ticket</th><th>Asunto</th><th>Área</th><th>Prioridad</th><th>Asignado</th><th>Estado</th><th>Abierto</th></tr></thead>
          <tbody>
            @for (t of visible(); track t.id) {
              <tr (click)="open(t)">
                <td class="nx-mono">{{ t.codigo }}</td>
                <td><b>{{ t.asunto }}</b></td>
                <td>{{ t.categoria }}</td>
                <td><nx-badge [status]="t.prioridad" /></td>
                <td>{{ t.asignadoA ? team.name(t.asignadoA) : 'Sin asignar' }}</td>
                <td><nx-badge [status]="t.estado" /></td>
                <td class="nx-nowrap">{{ t.creadoEn | fecha: 'relativa' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </nx-state>

    <nx-drawer [open]="newOpen()" (openChange)="newOpen.set($event)" title="Nuevo ticket" [dismissible]="!busy()">
      <div class="nx-stack">
        <label class="nx-field"><span>Asunto</span><input class="nx-input" maxlength="200" [(ngModel)]="subject" /></label>
        <div class="nx-grid nx-grid--2">
          <label class="nx-field">
            <span>Área</span>
            <select class="nx-select" [(ngModel)]="category">
              @for (c of categories; track c) { <option [value]="c">{{ c }}</option> }
            </select>
          </label>
          <label class="nx-field">
            <span>Prioridad</span>
            <select class="nx-select" [(ngModel)]="priority"><option value="BAJA">Baja</option><option value="MEDIA">Media</option><option value="ALTA">Alta</option><option value="CRITICA">Crítica</option></select>
          </label>
        </div>
        <label class="nx-field">
          <span>Proyecto</span>
          <select class="nx-select" [(ngModel)]="projectId"><option [ngValue]="null">General</option>@for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.nombre }}</option> }</select>
        </label>
        <label class="nx-field"><span>Descripción</span><textarea class="nx-textarea" [(ngModel)]="description" placeholder="Qué pasa, dónde y desde cuándo"></textarea></label>
      </div>
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="newOpen.set(false)" [disabled]="busy()">Cancelar</button>
        <button type="button" class="nx-btn nx-btn--primary" (click)="create()" [disabled]="busy() || !subject.trim() || !description.trim()">Crear ticket</button>
      </ng-container>
    </nx-drawer>

    <nx-drawer [open]="!!selected()" (openChange)="$event || selected.set(null)" width="620px" [title]="selected()?.asunto ?? ''" [subtitle]="selected() ? selected()!.codigo + ' · ' + selected()!.categoria : ''">
      @if (selected(); as t) {
        <div class="nx-stack">
          <div class="nx-row"><nx-badge [status]="t.estado" /><nx-badge [status]="t.prioridad" /><span class="nx-muted">Abierto {{ t.creadoEn | fecha: 'hora' }} por {{ team.name(t.solicitanteId) }}</span></div>
          <div class="nx-grid nx-grid--2">
            @if (canAssign) {
              <label class="nx-field">
                <span>Asignado a</span>
                <select class="nx-select nx-select--sm" [ngModel]="t.asignadoA" (ngModelChange)="assign(t, $event)" [disabled]="busy()">
                  <option [ngValue]="null" disabled>Sin asignar</option>
                  @for (m of team.active(); track m.id) { <option [ngValue]="m.id">{{ m.nombre }}</option> }
                </select>
              </label>
            }
            @if (canChangeStatus) {
              <label class="nx-field">
                <span>Estado</span>
                <select class="nx-select nx-select--sm" [ngModel]="t.estado" (ngModelChange)="changeStatus(t, $event)" [disabled]="busy()">
                  @for (s of statuses; track s) { <option [ngValue]="s">{{ s.charAt(0) + s.slice(1).toLowerCase().replace('_', ' ') }}</option> }
                </select>
              </label>
            }
          </div>
          <div class="thread">
            <article class="origin"><span>DESCRIPCIÓN</span><p>{{ t.descripcion }}</p></article>
            @for (m of messages(); track m.id) {
              <article [class.internal]="m.interno"><header><b>{{ team.name(m.usuarioId) }}</b><span>{{ m.interno ? 'Nota interna · ' : '' }}{{ m.creadoEn | fecha: 'hora' }}</span></header><p>{{ m.mensaje }}</p></article>
            }
          </div>
          @if (canReply && t.estado !== 'CERRADO') {
            <label class="nx-field"><span>Responder</span><textarea class="nx-textarea" [(ngModel)]="reply"></textarea></label>
            <div class="nx-row nx-row--between">
              <label class="nx-check"><input type="checkbox" [(ngModel)]="internal" /> Nota interna</label>
              <button type="button" class="nx-btn nx-btn--sm nx-btn--primary" (click)="send(t)" [disabled]="busy() || !reply.trim()">Enviar</button>
            </div>
          }
        </div>
      }
    </nx-drawer>
  `,
})
export class TicketsTabComponent {
  private readonly api = inject(SupportService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  readonly team = inject(TeamService);

  readonly projects = input<Project[]>([]);
  /** Borrador recibido al escalar una PQRS: abre el formulario con estos datos. */
  readonly draft = input<{ asunto: string; descripcion: string; proyectoId: number | null; key: number } | null>(null);

  readonly canCreate = this.auth.hasAuthority('SOPORTE_CREAR');
  readonly canAssign = this.auth.hasAuthority('SOPORTE_ASIGNAR');
  readonly canChangeStatus = this.auth.hasAuthority('SOPORTE_CAMBIAR_ESTADO');
  readonly canReply = this.auth.hasAuthority('SOPORTE_RESPONDER');
  readonly statusOptions = STATUS_OPTIONS;
  readonly statuses: TicketStatus[] = ['ABIERTO', 'EN_PROCESO', 'EN_ESPERA', 'RESUELTO', 'CERRADO', 'REABIERTO'];
  readonly categories = ['Soporte TI', 'Cartera', 'Ventas', 'Inventario', 'Jurídica', 'Administración', 'Otro'];

  readonly items = signal<SupportTicket[]>([]);
  readonly status = signal('ABIERTOS');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly newOpen = signal(false);
  readonly selected = signal<SupportTicket | null>(null);
  readonly messages = signal<TicketMessage[]>([]);
  readonly visible = computed(() => {
    const s = this.status();
    if (!s) return this.items();
    if (s === 'ABIERTOS') return this.items().filter((t) => !['RESUELTO', 'CERRADO'].includes(t.estado));
    return this.items().filter((t) => t.estado === s);
  });

  subject = '';
  category = 'Soporte TI';
  priority: ServicePriority = 'MEDIA';
  projectId: number | null = null;
  description = '';
  reply = '';
  internal = false;

  constructor() {
    this.team.load();
    this.load();
    effect(() => {
      const d = this.draft();
      if (d) untracked(() => {
        this.subject = d.asunto.slice(0, 200);
        this.description = d.descripcion;
        this.projectId = d.proyectoId;
        this.newOpen.set(true);
      });
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().subscribe({
      next: (page) => {
        this.items.set(page.contenido);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudieron cargar los tickets.'));
        this.loading.set(false);
      },
    });
  }

  openNew(): void {
    this.subject = '';
    this.description = '';
    this.projectId = null;
    this.newOpen.set(true);
  }

  create(): void {
    this.busy.set(true);
    this.api.create({ proyectoId: this.projectId, asunto: this.subject.trim(), categoria: this.category, prioridad: this.priority, descripcion: this.description.trim() }).subscribe({
      next: (t) => {
        this.busy.set(false);
        this.newOpen.set(false);
        this.toast.success(`Ticket ${t.codigo} creado.`);
        this.load();
      },
      error: (e) => this.failed(e),
    });
  }

  open(t: SupportTicket): void {
    this.selected.set(t);
    this.reply = '';
    this.messages.set([]);
    this.api.messages(t.id).subscribe({ next: (m) => this.messages.set(m), error: () => this.messages.set([]) });
  }

  assign(t: SupportTicket, userId: number): void {
    this.update(this.api.assign(t.id, userId), 'Ticket asignado.');
  }

  changeStatus(t: SupportTicket, estado: TicketStatus): void {
    this.update(this.api.changeStatus(t.id, estado), 'Estado actualizado.');
  }

  send(t: SupportTicket): void {
    this.busy.set(true);
    this.api.reply(t.id, this.reply.trim(), this.internal).subscribe({
      next: () => {
        this.busy.set(false);
        this.reply = '';
        this.open(t);
      },
      error: (e) => this.failed(e),
    });
  }

  private update(request: ReturnType<SupportService['assign']>, message: string): void {
    this.busy.set(true);
    request.subscribe({
      next: (t) => {
        this.busy.set(false);
        this.selected.set(t);
        this.toast.success(message);
        this.load();
      },
      error: (e) => this.failed(e),
    });
  }

  private failed(e: unknown): void {
    this.busy.set(false);
    this.toast.error(apiErrorMessage(e));
  }
}
