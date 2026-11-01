import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, Observable, of } from 'rxjs';

import { PageResponse } from '../../core/models/api.models';
import { Appointment, CommercialActivity, Opportunity, OpportunityLot, Prospect, StageHistory } from '../../core/models/crm.models';
import { Project } from '../../core/models/empresa.models';
import { Lot } from '../../core/models/inventory.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { InventoryService } from '../../core/services/inventory.service';
import { TeamService } from '../../core/services/team.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, formatDateTime, formatRelative, localDateTimeIso, NX_UI, TabItem, ToastService } from '../../shared/ui';
import {
  ACTIVITY_TYPES,
  activityOf,
  APPOINTMENT_TYPES,
  fromLocalInput,
  nextStage,
  sourceLabel,
  STAGES,
  stageOf,
  temperatureOf,
  toLocalInput,
} from './crm-labels';

type Mode = 'avanzar' | 'actividad' | 'cita' | 'perdida' | 'editar' | null;

interface TimelineItem {
  at: string;
  icon: string;
  title: string;
  detail: string | null;
  who: string;
  tone: 'stage' | 'activity' | 'appointment';
}

/** Detalle de una oportunidad: datos, actividad, lotes y acciones del embudo. */
@Component({
  selector: 'app-opportunity-drawer',
  imports: [...NX_UI, ReactiveFormsModule, MoneyInputDirective, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './opportunity-drawer.component.html',
  styleUrl: './crm.shared.scss',
})
export class OpportunityDrawerComponent {
  private readonly crm = inject(CrmService);
  private readonly inventory = inject(InventoryService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly team = inject(TeamService);

  readonly open = model(false);
  readonly opportunityId = input<number | null>(null);
  readonly projects = input<Project[]>([]);
  readonly changed = output<void>();

  readonly stages = STAGES;
  readonly activityTypes = ACTIVITY_TYPES;
  readonly appointmentTypes = APPOINTMENT_TYPES;
  readonly can = {
    advance: this.auth.hasAuthority('OPORTUNIDADES_AVANZAR'),
    edit: this.auth.hasAuthority('OPORTUNIDADES_EDITAR'),
    lose: this.auth.hasAuthority('OPORTUNIDADES_CERRAR_PERDIDA'),
    reopen: this.auth.hasAuthority('OPORTUNIDADES_REABRIR'),
    agenda: this.auth.hasAuthority('AGENDA_GESTIONAR'),
    seeAgenda: this.auth.hasAuthority('AGENDA_VER'),
    simulate: this.auth.hasAuthority('SIMULACIONES_CREAR') || this.auth.hasAuthority('SIMULACIONES_VER'),
  };

  readonly opportunity = signal<Opportunity | null>(null);
  readonly prospect = signal<Prospect | null>(null);
  readonly activities = signal<CommercialActivity[]>([]);
  readonly history = signal<StageHistory[]>([]);
  readonly appointments = signal<Appointment[]>([]);
  readonly lots = signal<OpportunityLot[]>([]);
  readonly projectLots = signal<Lot[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly mode = signal<Mode>(null);
  readonly section = signal('actividad');
  readonly sections: TabItem[] = [
    { id: 'actividad', label: 'Actividad' },
    { id: 'lotes', label: 'Lotes' },
  ];

  readonly advanceForm = this.fb.group({
    etapa: this.fb.control(''),
    probabilidad: this.fb.control(0, [Validators.min(0), Validators.max(100)]),
    observacion: this.fb.control('', Validators.maxLength(500)),
  });
  readonly activityForm = this.fb.group({
    tipo: this.fb.control<CommercialActivity['tipo']>('LLAMADA'),
    resultado: this.fb.control('', Validators.maxLength(120)),
    descripcion: this.fb.control('', [Validators.required, Validators.maxLength(5000)]),
    fechaActividad: this.fb.control(toLocalInput(new Date()), Validators.required),
    proximaAccion: this.fb.control('', Validators.maxLength(200)),
    fechaProximaAccion: this.fb.control(''),
  });
  readonly appointmentForm = this.fb.group({
    tipo: this.fb.control('VISITA', Validators.required),
    fechaInicio: this.fb.control('', Validators.required),
    duracion: this.fb.control(60, [Validators.required, Validators.min(15)]),
    lugar: this.fb.control('', Validators.maxLength(300)),
  });
  readonly loseForm = this.fb.group({ motivo: this.fb.control('', [Validators.required, Validators.maxLength(300)]) });
  readonly editForm = this.fb.group({
    valorEstimado: this.fb.control<number | null>(null),
    fechaProximaAccion: this.fb.control(''),
  });
  readonly lotToAdd = signal('');

  readonly stage = computed(() => stageOf(this.opportunity()?.etapaActual));
  readonly next = computed(() => {
    const o = this.opportunity();
    return o ? nextStage(o.etapaActual) : undefined;
  });
  readonly stageIndex = computed(() => STAGES.findIndex((s) => s.value === this.opportunity()?.etapaActual));
  readonly isOpen = computed(() => this.opportunity()?.estado === 'ABIERTA');
  readonly isLate = computed(() => {
    const next = this.opportunity()?.fechaProximaAccion;
    return !!next && this.isOpen() && next < localDateTimeIso(new Date());
  });
  readonly temperature = computed(() => temperatureOf(this.prospect()?.temperatura));
  readonly source = computed(() => sourceLabel(this.prospect()?.fuente));
  readonly projectName = computed(() => this.projects().find((p) => p.id === this.opportunity()?.proyectoId)?.nombre ?? '');
  readonly title = computed(() => this.prospect()?.nombres ?? this.opportunity()?.codigo ?? 'Oportunidad');
  readonly subtitle = computed(() => [this.opportunity()?.codigo, this.projectName()].filter(Boolean).join(' · '));
  readonly lotsWithInfo = computed(() => {
    const byId = new Map(this.projectLots().map((l) => [l.id, l]));
    return [...this.lots()].sort((a, b) => a.prioridad - b.prioridad).map((r) => ({ relation: r, lot: byId.get(r.loteId) ?? null }));
  });
  readonly availableLots = computed(() => {
    const taken = new Set(this.lots().map((l) => l.loteId));
    return this.projectLots().filter((l) => l.disponible && !taken.has(l.id));
  });
  readonly timeline = computed<TimelineItem[]>(() => {
    const items: TimelineItem[] = [
      ...this.activities().map((a) => ({
        at: a.fechaActividad,
        icon: activityOf(a.tipo).icon,
        title: `${activityOf(a.tipo).label}${a.resultado ? ' · ' + a.resultado : ''}`,
        detail: a.descripcion + (a.proximaAccion ? `\nPróximo paso: ${a.proximaAccion}` : ''),
        who: this.team.name(a.asesorId),
        tone: 'activity' as const,
      })),
      ...this.history().map((h) => ({
        at: h.creadoEn,
        icon: 'trend-up',
        title: h.etapaAnterior ? `${stageOf(h.etapaAnterior)?.label ?? h.etapaAnterior} → ${stageOf(h.etapaNueva)?.label ?? h.etapaNueva}` : `Creada en ${stageOf(h.etapaNueva)?.label ?? h.etapaNueva}`,
        detail: h.observacion,
        who: this.team.name(h.usuarioId),
        tone: 'stage' as const,
      })),
      ...this.appointments().map((c) => ({
        at: c.fechaInicio,
        icon: 'calendar',
        title: `Cita · ${c.tipo.toLowerCase()} (${c.estado.toLowerCase().replace('_', ' ')})`,
        detail: [c.lugar, c.resultado].filter(Boolean).join(' · ') || null,
        who: this.team.name(c.asesorId),
        tone: 'appointment' as const,
      })),
    ];
    return items.sort((a, b) => b.at.localeCompare(a.at));
  });

  constructor() {
    effect(() => {
      const id = this.opportunityId();
      const open = this.open();
      if (id && open) untracked(() => this.load(id));
    });
  }

  load(id = this.opportunityId()): void {
    if (!id) return;
    this.loading.set(true);
    this.mode.set(null);
    const safe = <T>(o: Observable<T>, fallback: T) => o.pipe(catchError(() => of(fallback)));
    this.crm.opportunity(id).subscribe({
      next: (o) => {
        this.opportunity.set(o);
        forkJoin({
          prospect: safe(this.crm.prospect(o.prospectoId), null as Prospect | null),
          activities: safe(this.crm.activities(id), [] as CommercialActivity[]),
          history: safe(this.crm.stageHistory(id), [] as StageHistory[]),
          appointments: this.can.seeAgenda ? safe(this.crm.opportunityAppointments(id), [] as Appointment[]) : of([] as Appointment[]),
          lots: safe(this.crm.opportunityLots(id), [] as OpportunityLot[]),
          projectLots: safe(this.inventory.listLots({ projectId: o.proyectoId, size: 100 }), null as PageResponse<Lot> | null),
        }).subscribe((r) => {
          this.prospect.set(r.prospect);
          this.activities.set(r.activities);
          this.history.set(r.history);
          this.appointments.set(r.appointments);
          this.lots.set(r.lots);
          this.projectLots.set(r.projectLots?.contenido ?? []);
          this.loading.set(false);
        });
      },
      error: (e) => {
        this.loading.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo abrir la oportunidad.'));
        this.open.set(false);
      },
    });
  }

  setMode(mode: Mode): void {
    const o = this.opportunity();
    if (!o) return;
    if (mode === 'avanzar') {
      const n = this.next();
      this.advanceForm.reset({ etapa: n?.value ?? o.etapaActual, probabilidad: n?.probability ?? Number(o.probabilidadCierre), observacion: '' });
    }
    if (mode === 'actividad') this.activityForm.reset({ tipo: 'LLAMADA', resultado: '', descripcion: '', fechaActividad: toLocalInput(new Date()), proximaAccion: '', fechaProximaAccion: '' });
    if (mode === 'cita') {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      tomorrow.setHours(9, 0, 0, 0);
      this.appointmentForm.reset({ tipo: 'VISITA', fechaInicio: toLocalInput(tomorrow), duracion: 60, lugar: this.projectName() });
    }
    if (mode === 'perdida') this.loseForm.reset({ motivo: '' });
    if (mode === 'editar') this.editForm.reset({ valorEstimado: o.valorEstimado, fechaProximaAccion: o.fechaProximaAccion?.slice(0, 16) ?? '' });
    this.mode.set(this.mode() === mode ? null : mode);
  }

  pickStage(value: string): void {
    const s = stageOf(value as Opportunity['etapaActual']);
    if (s) this.advanceForm.patchValue({ probabilidad: s.probability });
  }

  submitAdvance(): void {
    const o = this.opportunity();
    const v = this.advanceForm.getRawValue();
    if (!o || !v.etapa) return;
    const label = stageOf(v.etapa as Opportunity['etapaActual'])?.label ?? v.etapa;
    this.run(this.crm.moveOpportunity(o.id, v.etapa as Opportunity['etapaActual'], v.probabilidad, v.observacion.trim()), `Oportunidad en ${label}.`);
  }

  submitActivity(): void {
    const o = this.opportunity();
    if (!o || this.activityForm.invalid) return this.activityForm.markAllAsTouched();
    const v = this.activityForm.getRawValue();
    this.run(
      this.crm.addActivity(o.id, {
        tipo: v.tipo,
        resultado: v.resultado.trim() || null,
        descripcion: v.descripcion.trim(),
        fechaActividad: fromLocalInput(v.fechaActividad)!,
        proximaAccion: v.proximaAccion.trim() || null,
        fechaProximaAccion: fromLocalInput(v.fechaProximaAccion),
      }),
      'Actividad registrada.',
    );
  }

  submitAppointment(): void {
    const o = this.opportunity();
    if (!o || this.appointmentForm.invalid) return this.appointmentForm.markAllAsTouched();
    const v = this.appointmentForm.getRawValue();
    const start = new Date(v.fechaInicio);
    const end = new Date(start.getTime() + v.duracion * 60_000);
    this.run(
      this.crm.createAppointment(o.id, {
        tipo: v.tipo,
        fechaInicio: fromLocalInput(v.fechaInicio)!,
        fechaFin: fromLocalInput(toLocalInput(end))!,
        lugar: v.lugar.trim() || null,
      }),
      'Cita agendada.',
    );
  }

  submitLose(): void {
    const o = this.opportunity();
    if (!o || this.loseForm.invalid) return this.loseForm.markAllAsTouched();
    this.run(this.crm.loseOpportunity(o.id, this.loseForm.getRawValue().motivo.trim()), 'Oportunidad cerrada como perdida.');
  }

  reopen(): void {
    const o = this.opportunity();
    if (o) this.run(this.crm.reopenOpportunity(o.id), 'Oportunidad reabierta.');
  }

  submitEdit(): void {
    const o = this.opportunity();
    if (!o) return;
    const v = this.editForm.getRawValue();
    this.run(this.crm.updateOpportunity(o.id, v.valorEstimado, fromLocalInput(v.fechaProximaAccion)), 'Oportunidad actualizada.');
  }

  addLot(): void {
    const o = this.opportunity();
    const lotId = Number(this.lotToAdd());
    if (!o || !lotId) return;
    this.run(this.crm.addOpportunityLot(o.id, lotId, this.lots().length + 1), 'Lote vinculado.', () => this.lotToAdd.set(''));
  }

  selectLot(r: OpportunityLot): void {
    const o = this.opportunity();
    if (o) this.run(this.crm.selectOpportunityLot(o.id, r.loteId, true), 'Lote marcado como el elegido.');
  }

  removeLot(r: OpportunityLot): void {
    const o = this.opportunity();
    if (o) this.run(this.crm.removeOpportunityLot(o.id, r.loteId), 'Lote desvinculado.');
  }

  when(value: string | null): string {
    return value ? `${formatDateTime(value)} · ${formatRelative(value)}` : '—';
  }

  private run(request: Observable<unknown>, message: string, done?: () => void): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(message);
        done?.();
        this.changed.emit();
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }
}
