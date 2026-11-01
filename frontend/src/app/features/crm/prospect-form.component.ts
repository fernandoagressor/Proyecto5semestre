import { ChangeDetectionStrategy, Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Prospect, ProspectSource, ProspectTemperature } from '../../core/models/crm.models';
import { Project } from '../../core/models/empresa.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { TeamService } from '../../core/services/team.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, NX_UI, ToastService } from '../../shared/ui';
import { SOURCES, TEMPERATURES } from './crm-labels';

/** Crear o editar un prospecto en un panel lateral. */
@Component({
  selector: 'app-prospect-form',
  imports: [...NX_UI, ReactiveFormsModule, MoneyInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nx-drawer [(open)]="open" [title]="prospect() ? 'Editar prospecto' : 'Nuevo prospecto'"
      [subtitle]="prospect() ? prospect()!.nombres : 'Datos de contacto e interés del cliente potencial.'" [dismissible]="!saving()">
      <form class="nx-stack" [formGroup]="form" (ngSubmit)="save()" id="prospect-form">
        <label class="nx-field">
          <span>Nombre completo</span>
          <input class="nx-input" formControlName="nombres" autocomplete="off" />
          @if (form.controls.nombres.touched && form.controls.nombres.invalid) { <span class="nx-field__error">Escriba el nombre.</span> }
        </label>
        <div class="nx-grid nx-grid--2">
          <label class="nx-field"><span>Teléfono</span><input class="nx-input" type="tel" formControlName="telefono" /></label>
          <label class="nx-field">
            <span>Correo</span>
            <input class="nx-input" type="email" formControlName="correo" />
            @if (form.controls.correo.touched && form.controls.correo.invalid) { <span class="nx-field__error">Correo no válido.</span> }
          </label>
        </div>
        <div class="nx-grid nx-grid--2">
          <label class="nx-field">
            <span>Fuente</span>
            <select class="nx-select" formControlName="fuente">
              @for (s of sources; track s.value) { <option [value]="s.value">{{ s.label }}</option> }
            </select>
          </label>
          <label class="nx-field">
            <span>Temperatura</span>
            <select class="nx-select" formControlName="temperatura">
              @for (t of temperatures; track t.value) { <option [value]="t.value">{{ t.label }}</option> }
            </select>
          </label>
        </div>
        <div class="nx-grid nx-grid--2">
          <label class="nx-field">
            <span>Proyecto de interés</span>
            <select class="nx-select" formControlName="proyectoId">
              <option value="">Sin definir</option>
              @for (p of projects(); track p.id) { <option [value]="p.id">{{ p.nombre }}</option> }
            </select>
          </label>
          <label class="nx-field"><span>Presupuesto</span><input class="nx-input" nexoraMoney formControlName="presupuesto" placeholder="$ 0" /></label>
        </div>
        @if (canAssign) {
          <label class="nx-field">
            <span>Asesor</span>
            <select class="nx-select" formControlName="asesorId">
              <option value="">Yo mismo</option>
              @for (m of team.advisors(); track m.id) { <option [value]="m.id">{{ m.nombre }}</option> }
            </select>
          </label>
        }
        <label class="nx-field"><span>Observaciones</span><textarea class="nx-textarea" formControlName="observaciones"></textarea></label>
      </form>
      <ng-container drawer-footer>
        <button type="button" class="nx-btn" (click)="open.set(false)" [disabled]="saving()">Cancelar</button>
        <button type="submit" form="prospect-form" class="nx-btn nx-btn--primary" [disabled]="saving()">
          @if (saving()) { <span class="nx-spinner"></span> } {{ prospect() ? 'Guardar cambios' : 'Crear prospecto' }}
        </button>
      </ng-container>
    </nx-drawer>
  `,
})
export class ProspectFormComponent {
  private readonly crm = inject(CrmService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly team = inject(TeamService);

  readonly open = model(false);
  readonly prospect = input<Prospect | null>(null);
  readonly projects = input<Project[]>([]);
  readonly defaultProjectId = input<number | null>(null);
  readonly saved = output<Prospect>();

  readonly sources = SOURCES;
  readonly temperatures = TEMPERATURES;
  readonly canAssign = inject(AuthService).hasAuthority('PROSPECTOS_REASIGNAR');
  readonly saving = signal(false);

  readonly form = this.fb.group({
    nombres: this.fb.control('', [Validators.required, Validators.maxLength(160)]),
    telefono: this.fb.control('', Validators.maxLength(40)),
    correo: this.fb.control('', [Validators.email, Validators.maxLength(180)]),
    fuente: this.fb.control<ProspectSource>('WEB'),
    temperatura: this.fb.control<ProspectTemperature>('TIBIO'),
    proyectoId: this.fb.control(''),
    presupuesto: this.fb.control<number | null>(null),
    asesorId: this.fb.control(''),
    observaciones: this.fb.control('', Validators.maxLength(5000)),
  });

  constructor() {
    effect(() => {
      if (!this.open()) return;
      const p = this.prospect();
      const project = this.defaultProjectId();
      untracked(() =>
        this.form.reset({
          nombres: p?.nombres ?? '',
          telefono: p?.telefono ?? '',
          correo: p?.correo ?? '',
          fuente: p?.fuente ?? 'WEB',
          temperatura: p?.temperatura ?? 'TIBIO',
          proyectoId: p?.proyectoId ? String(p.proyectoId) : project ? String(project) : '',
          presupuesto: p?.presupuesto ?? null,
          asesorId: p?.asesorId ? String(p.asesorId) : '',
          observaciones: p?.observaciones ?? '',
        }),
      );
    });
  }

  save(): void {
    if (this.form.invalid) return this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    const payload = {
      nombres: v.nombres.trim(),
      telefono: v.telefono.trim() || null,
      correo: v.correo.trim() || null,
      fuente: v.fuente,
      temperatura: v.temperatura,
      proyectoId: v.proyectoId ? Number(v.proyectoId) : null,
      presupuesto: v.presupuesto,
      asesorId: v.asesorId ? Number(v.asesorId) : (this.prospect()?.asesorId ?? null),
      observaciones: v.observaciones.trim() || null,
    };
    const current = this.prospect();
    this.saving.set(true);
    (current ? this.crm.updateProspect(current.id, payload) : this.crm.createProspect(payload)).subscribe({
      next: (p) => {
        this.saving.set(false);
        this.open.set(false);
        this.toast.success(current ? 'Prospecto actualizado.' : `Prospecto ${p.nombres} creado.`);
        this.saved.emit(p);
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.error(apiErrorMessage(e, 'No se pudo guardar el prospecto.'));
      },
    });
  }
}
