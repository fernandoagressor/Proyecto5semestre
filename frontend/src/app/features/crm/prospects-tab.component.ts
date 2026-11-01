import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { PageResponse } from '../../core/models/api.models';
import { Prospect, ProspectStatus, ProspectTemperature } from '../../core/models/crm.models';
import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { TeamService } from '../../core/services/team.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, ChipOption, NX_UI, ToastService } from '../../shared/ui';
import { fromLocalInput, PROSPECT_STATUSES, sourceLabel, TEMPERATURES, temperatureOf } from './crm-labels';
import { ProspectFormComponent } from './prospect-form.component';

type Dialog = 'calificar' | 'oportunidad' | 'reasignar' | null;

@Component({
  selector: 'app-prospects-tab',
  imports: [...NX_UI, ReactiveFormsModule, MoneyInputDirective, ProspectFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './prospects-tab.component.html',
  styleUrl: './crm.shared.scss',
})
export class ProspectsTabComponent {
  private readonly crm = inject(CrmService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly team = inject(TeamService);

  readonly projectId = input<number | null>(null);
  readonly projects = input<Project[]>([]);
  readonly search = input('');
  /** Se incrementa desde la página para recargar (por ejemplo, tras crear un prospecto). */
  readonly reloadKey = input(0);
  readonly openOpportunity = output<number>();

  readonly can = {
    edit: this.auth.hasAuthority('PROSPECTOS_EDITAR'),
    qualify: this.auth.hasAuthority('PROSPECTOS_CALIFICAR'),
    reassign: this.auth.hasAuthority('PROSPECTOS_REASIGNAR'),
    opportunity: this.auth.hasAuthority('OPORTUNIDADES_CREAR'),
  };
  readonly temperatures = TEMPERATURES;
  readonly statuses = PROSPECT_STATUSES;
  readonly statusOptions: ChipOption[] = [{ value: '', label: 'Todos' }, ...PROSPECT_STATUSES.map((s) => ({ value: s.value, label: s.label }))];

  readonly status = signal('');
  readonly temperature = signal('');
  readonly page = signal(0);
  readonly data = signal<PageResponse<Prospect> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);

  readonly target = signal<Prospect | null>(null);
  readonly dialog = signal<Dialog>(null);
  readonly dialogOpen = signal(false);
  readonly editOpen = signal(false);

  readonly qualifyForm = this.fb.group({
    estado: this.fb.control<ProspectStatus>('CALIFICADO'),
    temperatura: this.fb.control<ProspectTemperature>('TIBIO'),
    justificacion: this.fb.control('', Validators.maxLength(5000)),
  });
  readonly opportunityForm = this.fb.group({
    proyectoId: this.fb.control('', Validators.required),
    asesorId: this.fb.control(''),
    valorEstimado: this.fb.control<number | null>(null),
    fechaProximaAccion: this.fb.control(''),
  });
  readonly reassignTo = signal('');

  readonly rows = computed(() => this.data()?.contenido ?? []);
  readonly dialogTitle = computed(() => ({ calificar: 'Calificar prospecto', oportunidad: 'Crear oportunidad', reasignar: 'Reasignar asesor' })[this.dialog() ?? 'calificar']);

  constructor() {
    effect(() => {
      const query = { project: this.projectId(), text: this.search(), status: this.status(), temperature: this.temperature(), page: this.page(), key: this.reloadKey() };
      untracked(() => this.load(query));
    });
  }

  load(q = { project: this.projectId(), text: this.search(), status: this.status(), temperature: this.temperature(), page: this.page() }): void {
    this.loading.set(true);
    this.error.set(null);
    this.crm.prospects({ projectId: q.project, text: q.text, status: q.status, temperature: q.temperature, page: q.page, size: 25 }).subscribe({
      next: (page) => {
        this.data.set(page);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(apiErrorMessage(e, 'No se pudieron cargar los prospectos.'));
        this.loading.set(false);
      },
    });
  }

  setStatus(value: string): void {
    this.status.set(value);
    this.page.set(0);
  }

  setTemperature(value: string): void {
    this.temperature.set(value);
    this.page.set(0);
  }

  temp(p: Prospect) {
    return temperatureOf(p.temperatura);
  }

  source(p: Prospect): string {
    return sourceLabel(p.fuente);
  }

  projectName(id: number | null): string {
    return id ? (this.projects().find((p) => p.id === id)?.nombre ?? `#${id}`) : '—';
  }

  edit(p: Prospect): void {
    this.target.set(p);
    this.editOpen.set(true);
  }

  ask(p: Prospect, dialog: Dialog): void {
    this.target.set(p);
    this.dialog.set(dialog);
    if (dialog === 'calificar') this.qualifyForm.reset({ estado: p.estado === 'NUEVO' ? 'CONTACTADO' : 'CALIFICADO', temperatura: p.temperatura, justificacion: '' });
    if (dialog === 'oportunidad')
      this.opportunityForm.reset({
        proyectoId: p.proyectoId ? String(p.proyectoId) : this.projectId() ? String(this.projectId()) : '',
        asesorId: p.asesorId ? String(p.asesorId) : '',
        valorEstimado: p.presupuesto,
        fechaProximaAccion: '',
      });
    if (dialog === 'reasignar') this.reassignTo.set(p.asesorId ? String(p.asesorId) : '');
    this.dialogOpen.set(true);
  }

  confirm(): void {
    const p = this.target();
    if (!p) return;
    const dialog = this.dialog();
    if (dialog === 'calificar') {
      const v = this.qualifyForm.getRawValue();
      this.run(this.crm.qualifyProspect(p.id, v.estado, v.temperatura, v.justificacion.trim()), 'Prospecto calificado.');
    } else if (dialog === 'oportunidad') {
      if (this.opportunityForm.invalid) return this.opportunityForm.markAllAsTouched();
      const v = this.opportunityForm.getRawValue();
      this.busy.set(true);
      this.crm
        .createOpportunity({
          proyectoId: Number(v.proyectoId),
          prospectoId: p.id,
          asesorId: v.asesorId ? Number(v.asesorId) : null,
          valorEstimado: v.valorEstimado,
          fechaProximaAccion: fromLocalInput(v.fechaProximaAccion),
        })
        .subscribe({
          next: (o) => {
            this.busy.set(false);
            this.dialogOpen.set(false);
            this.toast.success(`Oportunidad ${o.codigo} creada.`);
            this.load();
            this.openOpportunity.emit(o.id);
          },
          error: (e) => {
            this.busy.set(false);
            this.toast.error(apiErrorMessage(e, 'No se pudo crear la oportunidad.'));
          },
        });
    } else if (dialog === 'reasignar' && this.reassignTo()) {
      this.run(this.crm.reassignProspect(p.id, Number(this.reassignTo())), 'Prospecto reasignado.');
    }
  }

  private run(request: ReturnType<CrmService['prospect']>, message: string): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.dialogOpen.set(false);
        this.toast.success(message);
        this.load();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }
}
