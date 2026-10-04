import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, finalize, forkJoin, map, Observable, of } from 'rxjs';

import { Project } from '../../core/models/empresa.models';
import { Customer } from '../../core/models/sales.models';
import {
  ClientDataChange,
  ClientPortalDocument,
  ClientPortalSummary,
  ServiceAppointment,
  ServiceAppointmentStatus,
  ServiceAttachment,
  ServiceHistory,
  ServiceMessage,
  ServicePriority,
  ServiceRequest,
  ServiceRequestStatus,
  ServiceRequestType,
} from '../../core/models/service.models';
import { AuthService } from '../../core/services/auth.service';
import { CustomerServiceService } from '../../core/services/customer-service.service';
import { ProjectService } from '../../core/services/project.service';
import { SalesService } from '../../core/services/sales.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';

type ServiceTab = 'resumen' | 'solicitudes' | 'citas' | 'portal';
type ServiceModal = 'solicitud' | 'detalle' | 'cita' | 'reprogramar' | 'datos' | null;
type DetailView = 'conversacion' | 'adjuntos' | 'historial';

@Component({
  selector: 'app-customer-service-page',
  imports: [CommonModule, ReactiveFormsModule, AppIconComponent],
  templateUrl: './customer-service.page.html',
  styleUrl: './customer-service.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerServicePage implements OnInit {
  readonly auth = inject(AuthService);
  private readonly service = inject(CustomerServiceService);
  private readonly sales = inject(SalesService);
  private readonly projectsService = inject(ProjectService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeTab = signal<ServiceTab>('resumen');
  readonly modal = signal<ServiceModal>(null);
  readonly detailView = signal<DetailView>('conversacion');
  readonly requests = signal<ServiceRequest[]>([]);
  readonly appointments = signal<ServiceAppointment[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly messages = signal<ServiceMessage[]>([]);
  readonly attachments = signal<ServiceAttachment[]>([]);
  readonly history = signal<ServiceHistory[]>([]);
  readonly portalSummary = signal<ClientPortalSummary | null>(null);
  readonly portalDocuments = signal<ClientPortalDocument[]>([]);
  readonly dataChanges = signal<ClientDataChange[]>([]);
  readonly selectedRequest = signal<ServiceRequest | null>(null);
  readonly selectedAppointment = signal<ServiceAppointment | null>(null);
  readonly selectedFile = signal<File | null>(null);
  readonly loading = signal(true);
  readonly detailLoading = signal(false);
  readonly saving = signal(false);
  readonly uploading = signal(false);
  readonly feedback = signal('');
  readonly error = signal('');
  readonly requestStatus = signal<ServiceRequestStatus | ''>('');
  readonly requestType = signal<ServiceRequestType | ''>('');
  readonly appointmentStatus = signal<ServiceAppointmentStatus | ''>('');
  readonly search = signal('');

  readonly isClient = computed(() => this.auth.hasAuthority('ROLE_CLIENTE'));
  readonly openRequests = computed(() =>
    this.requests().filter((item) => !['CERRADA', 'CANCELADA'].includes(item.estado)).length,
  );
  readonly overdueRequests = computed(() => {
    const now = Date.now();
    return this.requests().filter(
      (item) =>
        !['RESUELTA', 'CERRADA', 'CANCELADA'].includes(item.estado) &&
        new Date(item.fechaLimite).getTime() < now,
    ).length;
  });
  readonly upcomingAppointments = computed(() =>
    this.appointments().filter(
      (item) =>
        ['PROGRAMADA', 'CONFIRMADA', 'REPROGRAMADA'].includes(item.estado) &&
        new Date(item.fechaInicio).getTime() >= Date.now(),
    ).length,
  );
  readonly unresolvedCritical = computed(() =>
    this.requests().filter(
      (item) =>
        item.prioridad === 'CRITICA' && !['RESUELTA', 'CERRADA', 'CANCELADA'].includes(item.estado),
    ).length,
  );
  readonly filteredRequests = computed(() => {
    const query = this.search().trim().toLowerCase();
    if (!query) return this.requests();
    return this.requests().filter((item) =>
      [item.codigo, item.asunto, item.categoria, this.customerName(item.clienteId)]
        .join(' ')
        .toLowerCase()
        .includes(query),
    );
  });

  readonly requestForm = this.fb.nonNullable.group({
    customerId: [0],
    projectId: [0],
    type: ['PETICION' as ServiceRequestType, Validators.required],
    category: ['', [Validators.required, Validators.maxLength(60)]],
    subject: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', [Validators.required, Validators.maxLength(10000)]],
    priority: ['MEDIA' as ServicePriority, Validators.required],
  });

  readonly messageForm = this.fb.nonNullable.group({
    message: ['', [Validators.required, Validators.maxLength(10000)]],
    internal: [false],
  });

  readonly statusForm = this.fb.nonNullable.group({
    status: ['EN_PROCESO' as ServiceRequestStatus, Validators.required],
    reason: ['', Validators.maxLength(500)],
  });

  readonly appointmentForm = this.fb.nonNullable.group({
    customerId: [0],
    projectId: [0],
    requestId: [0],
    responsibleId: [0],
    type: ['ASESORIA', [Validators.required, Validators.maxLength(30)]],
    start: ['', Validators.required],
    end: ['', Validators.required],
    place: ['', Validators.maxLength(300)],
  });

  readonly rescheduleForm = this.fb.nonNullable.group({
    start: ['', Validators.required],
    end: ['', Validators.required],
    place: ['', Validators.maxLength(300)],
  });

  readonly dataChangeForm = this.fb.nonNullable.group({
    field: ['TELEFONO', Validators.required],
    value: ['', [Validators.required, Validators.maxLength(500)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    if (this.isClient()) this.activeTab.set('portal');
    this.loadAll();
  }

  setTab(tab: ServiceTab): void {
    this.activeTab.set(tab);
    this.clearMessages();
    if (tab === 'portal' && this.isClient() && !this.portalSummary()) this.loadPortal();
  }

  refresh(): void {
    this.clearMessages();
    this.loadAll();
  }

  filterRequests(status: string, type = this.requestType()): void {
    this.requestStatus.set(status as ServiceRequestStatus | '');
    this.requestType.set(type as ServiceRequestType | '');
    this.loadRequests();
  }

  filterRequestType(type: string): void {
    this.filterRequests(this.requestStatus(), type as ServiceRequestType | '');
  }

  filterAppointments(status: string): void {
    this.appointmentStatus.set(status as ServiceAppointmentStatus | '');
    this.loadAppointments();
  }

  searchRequests(value: string): void {
    this.search.set(value);
  }

  openRequestForm(): void {
    this.clearMessages();
    this.requestForm.reset({
      customerId: 0,
      projectId: 0,
      type: 'PETICION',
      category: '',
      subject: '',
      description: '',
      priority: 'MEDIA',
    });
    this.modal.set('solicitud');
  }

  saveRequest(): void {
    if (this.requestForm.invalid) {
      this.requestForm.markAllAsTouched();
      return;
    }
    const value = this.requestForm.getRawValue();
    if (!this.isClient() && value.customerId < 1) {
      this.error.set('Selecciona el cliente de la solicitud.');
      return;
    }
    this.saving.set(true);
    this.clearMessages();
    this.service
      .createRequest({
        proyectoId: this.positiveOrNull(value.projectId),
        clienteId: this.isClient() ? null : this.positiveOrNull(value.customerId),
        contratoId: null,
        tipo: value.type,
        categoria: value.category.trim(),
        asunto: value.subject.trim(),
        descripcion: value.description.trim(),
        prioridad: value.priority,
      })
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.requests.update((items) => [created, ...items]);
          this.modal.set(null);
          this.feedback.set(`La solicitud ${created.codigo} quedó radicada correctamente.`);
          this.activeTab.set('solicitudes');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openRequest(request: ServiceRequest): void {
    this.clearMessages();
    this.selectedRequest.set(request);
    this.detailView.set('conversacion');
    this.messageForm.reset({ message: '', internal: false });
    this.statusForm.reset({ status: this.nextStatus(request.estado), reason: '' });
    this.messages.set([]);
    this.attachments.set([]);
    this.history.set([]);
    this.selectedFile.set(null);
    this.modal.set('detalle');
    this.loadRequestDetail(request.id);
  }

  showDetail(view: DetailView): void {
    this.detailView.set(view);
  }

  sendMessage(): void {
    const request = this.selectedRequest();
    if (!request || this.messageForm.invalid) {
      this.messageForm.markAllAsTouched();
      return;
    }
    const value = this.messageForm.getRawValue();
    this.saving.set(true);
    this.clearMessages();
    this.service
      .sendMessage(request.id, value.message.trim(), this.isClient() ? false : value.internal)
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (message) => {
          this.messages.update((items) => [...items, message]);
          this.messageForm.reset({ message: '', internal: false });
          this.feedback.set(value.internal ? 'La nota interna quedó registrada.' : 'La respuesta fue enviada.');
          this.loadRequests();
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  changeStatus(): void {
    const request = this.selectedRequest();
    if (!request || this.statusForm.invalid) return;
    const value = this.statusForm.getRawValue();
    const operation = value.status === 'CERRADA'
      ? this.service.closeRequest(request.id, this.nullIfBlank(value.reason))
      : this.service.changeRequestStatus(request.id, value.status, this.nullIfBlank(value.reason));
    this.saving.set(true);
    this.clearMessages();
    operation
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.selectedRequest.set(updated);
          this.requests.update((items) => items.map((item) => (item.id === updated.id ? updated : item)));
          this.feedback.set(`La solicitud quedó en estado ${this.label(updated.estado)}.`);
          this.statusForm.reset({ status: this.nextStatus(updated.estado), reason: '' });
          if (this.auth.hasAuthority('SOLICITUDES_ASIGNAR')) this.reloadHistory(updated.id);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  selectFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.item(0) ?? null);
  }

  uploadAttachment(): void {
    const request = this.selectedRequest();
    const file = this.selectedFile();
    if (!request || !file) {
      this.error.set('Selecciona un archivo PDF, PNG o JPG.');
      return;
    }
    this.uploading.set(true);
    this.clearMessages();
    this.service
      .uploadAttachment(request.id, file)
      .pipe(finalize(() => this.uploading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (attachment) => {
          this.attachments.update((items) => [...items, attachment]);
          this.selectedFile.set(null);
          this.feedback.set('El archivo quedó adjunto con trazabilidad.');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  downloadAttachment(attachment: ServiceAttachment): void {
    const request = this.selectedRequest();
    if (!request) return;
    this.download(
      this.service.downloadRequestAttachment(request.id, attachment.archivoId),
      attachment.nombreArchivo ?? `documento-${attachment.archivoId}`,
    );
  }

  downloadPortalDocument(document: ClientPortalDocument): void {
    this.download(this.service.downloadPortalDocument(document.archivoId), document.nombreArchivo);
  }

  private download(source: Observable<Blob>, name: string): void {
    source
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = name;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openAppointmentForm(request?: ServiceRequest): void {
    this.clearMessages();
    const start = this.futureDateTime(1);
    const end = this.futureDateTime(1, 1);
    this.appointmentForm.reset({
      customerId: this.isClient() ? 0 : request?.clienteId ?? 0,
      projectId: request?.proyectoId ?? 0,
      requestId: request?.id ?? 0,
      responsibleId: 0,
      type: 'ASESORIA',
      start,
      end,
      place: '',
    });
    this.modal.set('cita');
  }

  saveAppointment(): void {
    if (this.appointmentForm.invalid) {
      this.appointmentForm.markAllAsTouched();
      return;
    }
    const value = this.appointmentForm.getRawValue();
    if (!this.isClient() && value.customerId < 1) {
      this.error.set('Selecciona el cliente de la cita.');
      return;
    }
    if (value.end <= value.start) {
      this.error.set('La hora de finalización debe ser posterior al inicio.');
      return;
    }
    this.saving.set(true);
    this.clearMessages();
    this.service
      .createAppointment({
        proyectoId: this.positiveOrNull(value.projectId),
        clienteId: this.isClient() ? null : this.positiveOrNull(value.customerId),
        solicitudId: this.positiveOrNull(value.requestId),
        responsableId: this.positiveOrNull(value.responsibleId),
        tipo: value.type.trim().toUpperCase(),
        fechaInicio: value.start,
        fechaFin: value.end,
        lugar: this.nullIfBlank(value.place),
      })
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.appointments.update((items) => [...items, created].sort(this.sortAppointments));
          this.modal.set(null);
          this.activeTab.set('citas');
          this.feedback.set('La cita de servicio quedó programada.');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openReschedule(appointment: ServiceAppointment): void {
    this.clearMessages();
    this.selectedAppointment.set(appointment);
    this.rescheduleForm.reset({
      start: appointment.fechaInicio.slice(0, 16),
      end: appointment.fechaFin.slice(0, 16),
      place: appointment.lugar ?? '',
    });
    this.modal.set('reprogramar');
  }

  saveReschedule(): void {
    const appointment = this.selectedAppointment();
    if (!appointment || this.rescheduleForm.invalid) return;
    const value = this.rescheduleForm.getRawValue();
    if (value.end <= value.start) {
      this.error.set('La hora de finalización debe ser posterior al inicio.');
      return;
    }
    this.saving.set(true);
    this.service
      .rescheduleAppointment(
        appointment.id,
        value.start,
        value.end,
        this.nullIfBlank(value.place),
      )
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.updateAppointment(updated);
          this.modal.set(null);
          this.feedback.set('La cita quedó reprogramada.');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  updateAppointmentStatus(appointment: ServiceAppointment, status: ServiceAppointmentStatus): void {
    const result = status === 'CANCELADA' ? 'Cancelada desde el portal de servicio' : null;
    this.saving.set(true);
    this.clearMessages();
    this.service
      .changeAppointmentStatus(appointment.id, status, result)
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.updateAppointment(updated);
          this.feedback.set(`La cita quedó ${this.label(status).toLowerCase()}.`);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  openDataChange(): void {
    this.clearMessages();
    this.dataChangeForm.reset({ field: 'TELEFONO', value: '', reason: '' });
    this.modal.set('datos');
  }

  saveDataChange(): void {
    if (this.dataChangeForm.invalid) {
      this.dataChangeForm.markAllAsTouched();
      return;
    }
    const value = this.dataChangeForm.getRawValue();
    this.saving.set(true);
    this.service
      .requestDataChange(value.field, value.value.trim(), value.reason.trim())
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.dataChanges.update((items) => [created, ...items]);
          this.modal.set(null);
          this.feedback.set('La actualización quedó enviada para revisión independiente.');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  cancelDataChange(change: ClientDataChange): void {
    this.saving.set(true);
    this.service
      .cancelDataChange(change.id)
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.dataChanges.update((items) => items.map((item) => (item.id === updated.id ? updated : item)));
          this.feedback.set('La solicitud de cambio quedó cancelada.');
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  closeModal(): void {
    if (this.saving() || this.uploading()) return;
    this.modal.set(null);
    this.selectedRequest.set(null);
    this.selectedAppointment.set(null);
  }

  customerName(id: number): string {
    return this.customers().find((item) => item.id === id)?.nombreCompleto ?? `Cliente #${id}`;
  }

  projectName(id: number | null): string {
    if (!id) return 'Atención general';
    return this.projects().find((item) => item.id === id)?.nombre ?? `Proyecto #${id}`;
  }

  label(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
  }

  priorityClass(priority: ServicePriority): string {
    return `priority-${priority.toLowerCase()}`;
  }

  statusClass(status: string): string {
    return `status-${status.toLowerCase().replaceAll('_', '-')}`;
  }

  isOverdue(request: ServiceRequest): boolean {
    return (
      !['RESUELTA', 'CERRADA', 'CANCELADA'].includes(request.estado) &&
      new Date(request.fechaLimite).getTime() < Date.now()
    );
  }

  private loadAll(): void {
    this.loading.set(true);
    const range = this.appointmentRange();
    forkJoin({
      requests: this.service
        .requests({ status: this.requestStatus(), type: this.requestType() })
        .pipe(map((page) => page.contenido)),
      appointments: this.service
        .appointments({
          from: range.from,
          to: range.to,
          status: this.appointmentStatus(),
        })
        .pipe(map((page) => page.contenido)),
      customers: this.auth.hasAuthority('CLIENTES_VER')
        ? this.sales.customers({ status: 'ACTIVO', size: 100 }).pipe(
            map((page) => page.contenido),
            catchError(() => of([] as Customer[])),
          )
        : of([] as Customer[]),
      projects: !this.isClient() && this.auth.hasAuthority('PROYECTOS_VER')
        ? this.projectsService.list({ status: 'ACTIVO', size: 100 }).pipe(
            map((page) => page.contenido),
            catchError(() => of([] as Project[])),
          )
        : of([] as Project[]),
    })
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.requests.set(data.requests);
          this.appointments.set(data.appointments.sort(this.sortAppointments));
          this.customers.set(data.customers);
          this.projects.set(data.projects);
          if (this.isClient()) this.loadPortal();
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private loadRequests(): void {
    this.service
      .requests({ status: this.requestStatus(), type: this.requestType() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.requests.set(page.contenido),
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private loadAppointments(): void {
    const range = this.appointmentRange();
    this.service
      .appointments({ from: range.from, to: range.to, status: this.appointmentStatus() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.appointments.set(page.contenido.sort(this.sortAppointments)),
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private loadRequestDetail(id: number): void {
    this.detailLoading.set(true);
    forkJoin({
      messages: this.service.messages(id),
      attachments: this.service.attachments(id),
      history: this.auth.hasAuthority('SOLICITUDES_ASIGNAR') ? this.service.history(id) : of([]),
    })
      .pipe(finalize(() => this.detailLoading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.messages.set(data.messages);
          this.attachments.set(data.attachments);
          this.history.set(data.history);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private reloadHistory(id: number): void {
    this.service
      .history(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (history) => this.history.set(history) });
  }

  private loadPortal(): void {
    forkJoin({
      summary: this.service.portalSummary(),
      documents: this.auth.hasAuthority('DOCUMENTOS_VER')
        ? this.service.portalDocuments().pipe(map((page) => page.contenido))
        : of([] as ClientPortalDocument[]),
      changes: this.auth.hasAuthority('DATOS_CLIENTE_SOLICITAR_CAMBIO')
        ? this.service.dataChanges().pipe(map((page) => page.contenido))
        : of([] as ClientDataChange[]),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.portalSummary.set(data.summary);
          this.portalDocuments.set(data.documents);
          this.dataChanges.set(data.changes);
        },
        error: (error) => this.error.set(this.errorDetail(error)),
      });
  }

  private updateAppointment(updated: ServiceAppointment): void {
    this.appointments.update((items) =>
      items.map((item) => (item.id === updated.id ? updated : item)).sort(this.sortAppointments),
    );
    this.selectedAppointment.set(updated);
  }

  private appointmentRange(): { from: string; to: string } {
    const from = new Date();
    from.setHours(0, 0, 0, 0);
    const to = new Date(from);
    to.setDate(to.getDate() + 180);
    return { from: this.asLocalDateTime(from, true), to: this.asLocalDateTime(to, true) };
  }

  private futureDateTime(days: number, extraHours = 0): string {
    const date = new Date();
    date.setDate(date.getDate() + days);
    date.setHours(9 + extraHours, 0, 0, 0);
    return this.asLocalDateTime(date, false).slice(0, 16);
  }

  private asLocalDateTime(date: Date, seconds: boolean): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    const base = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
      `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    return seconds ? `${base}:${pad(date.getSeconds())}` : base;
  }

  private nextStatus(status: ServiceRequestStatus): ServiceRequestStatus {
    if (status === 'ABIERTA' || status === 'REABIERTA') return 'EN_PROCESO';
    if (status === 'EN_PROCESO') return 'PENDIENTE_CLIENTE';
    if (status === 'RESUELTA' && this.auth.hasAuthority('SOLICITUDES_CERRAR')) return 'CERRADA';
    return 'RESUELTA';
  }

  private positiveOrNull(value: number): number | null {
    return Number(value) > 0 ? Number(value) : null;
  }

  private nullIfBlank(value: string): string | null {
    const normalized = value.trim();
    return normalized || null;
  }

  private clearMessages(): void {
    this.feedback.set('');
    this.error.set('');
  }

  private errorDetail(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.detail ?? error.error?.title ?? 'No fue posible completar la operación.';
    }
    return error instanceof Error ? error.message : 'No fue posible completar la operación.';
  }

  private readonly sortAppointments = (a: ServiceAppointment, b: ServiceAppointment) =>
    a.fechaInicio.localeCompare(b.fechaInicio);
}
