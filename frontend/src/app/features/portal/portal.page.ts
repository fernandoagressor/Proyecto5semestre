import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';

import { Company } from '../../core/models/empresa.models';
import {
  ClientDataChange,
  ClientPortalDocument,
  ClientPortalSummary,
  PortalContract,
  PortalInstallment,
  PortalPayment,
  ServiceRequest,
  ServiceRequestType,
} from '../../core/models/service.models';
import { AuthService } from '../../core/services/auth.service';
import { CompanyService } from '../../core/services/company.service';
import { CustomerServiceService } from '../../core/services/customer-service.service';
import { apiErrorMessage, NX_UI, ToastService } from '../../shared/ui';
import { ToasterComponent } from '../../shared/ui/toast';

type Section = 'inicio' | 'pagos' | 'documentos' | 'ayuda' | 'perfil';

const REQUEST_TYPES: { value: ServiceRequestType; label: string }[] = [
  { value: 'PETICION', label: 'Petición' },
  { value: 'SOLICITUD', label: 'Solicitud' },
  { value: 'QUEJA', label: 'Queja' },
  { value: 'RECLAMO', label: 'Reclamo' },
  { value: 'SUGERENCIA', label: 'Sugerencia' },
];

const DATA_FIELDS = [
  { value: 'CORREO', label: 'Correo electrónico' },
  { value: 'TELEFONO', label: 'Teléfono' },
  { value: 'DIRECCION', label: 'Dirección' },
  { value: 'NOMBRES', label: 'Nombres' },
  { value: 'APELLIDOS', label: 'Apellidos' },
  { value: 'DOCUMENTO', label: 'Documento' },
];

/** Portal del cliente (vistas "Portal cliente web" y "móvil"): su lote, pagos, documentos y ayuda. */
@Component({
  selector: 'app-portal-page',
  imports: [...NX_UI, ReactiveFormsModule, RouterLink, ToasterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './portal.page.html',
  styleUrl: './portal.page.scss',
})
export class PortalPage {
  private readonly service = inject(CustomerServiceService);
  private readonly companyService = inject(CompanyService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);
  readonly auth = inject(AuthService);

  readonly can = {
    contracts: this.auth.hasAuthority('CONTRATOS_VER'),
    installments: this.auth.hasAuthority('CUOTAS_VER'),
    payments: this.auth.hasAuthority('PAGOS_VER'),
    documents: this.auth.hasAuthority('DOCUMENTOS_VER'),
    download: this.auth.hasAuthority('DOCUMENTOS_DESCARGAR'),
    requests: this.auth.hasAuthority('SOLICITUDES_VER'),
    createRequest: this.auth.hasAuthority('SOLICITUDES_CREAR'),
    dataChange: this.auth.hasAuthority('DATOS_CLIENTE_SOLICITAR_CAMBIO'),
  };
  readonly requestTypes = REQUEST_TYPES;
  readonly dataFields = DATA_FIELDS;
  readonly sections: { id: Section; label: string; icon: string }[] = [
    { id: 'inicio', label: 'Inicio', icon: 'dashboard' },
    { id: 'pagos', label: 'Pagos', icon: 'wallet' },
    { id: 'documentos', label: 'Documentos', icon: 'file' },
    { id: 'ayuda', label: 'Ayuda', icon: 'service' },
    { id: 'perfil', label: 'Mis datos', icon: 'user' },
  ];

  readonly section = signal<Section>('inicio');
  readonly loading = signal(true);
  readonly company = signal<Company | null>(null);
  readonly summary = signal<ClientPortalSummary | null>(null);
  readonly contracts = signal<PortalContract[]>([]);
  readonly contractId = signal<number | null>(null);
  readonly installments = signal<PortalInstallment[]>([]);
  readonly installmentsLoading = signal(false);
  readonly payments = signal<PortalPayment[]>([]);
  readonly documents = signal<ClientPortalDocument[]>([]);
  readonly requests = signal<ServiceRequest[]>([]);
  readonly changes = signal<ClientDataChange[]>([]);
  readonly saving = signal(false);
  readonly requestOpen = signal(false);
  readonly changeOpen = signal(false);
  readonly menuOpen = signal(false);

  readonly contract = computed(() => this.contracts().find((c) => c.id === this.contractId()) ?? this.contracts()[0] ?? null);
  readonly progress = computed(() => {
    const c = this.contract();
    return c && c.valorTotal > 0 ? Math.min(100, (c.pagado / c.valorTotal) * 100) : 0;
  });
  readonly daysToNext = computed(() => {
    const date = this.contract()?.proximaCuotaFecha;
    if (!date) return null;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((new Date(`${date}T00:00:00`).getTime() - today.getTime()) / 86_400_000);
  });
  readonly nextWhen = computed(() => {
    const d = this.daysToNext();
    if (d === null) return '';
    return d === 0 ? 'hoy' : d > 0 ? `en ${d} ${d === 1 ? 'día' : 'días'}` : `hace ${-d} ${d === -1 ? 'día' : 'días'}`;
  });
  readonly upcoming = computed(() =>
    this.installments().filter((q) => ['PENDIENTE', 'PARCIAL', 'VENCIDA'].includes(q.estado)).slice(0, 3),
  );
  readonly firstName = computed(() => {
    const name = this.auth.user()?.nombreUsuario ?? '';
    const first = name.split(/[.\s_@]/)[0] ?? name;
    return first.charAt(0).toUpperCase() + first.slice(1);
  });

  readonly requestForm = this.fb.group({
    tipo: this.fb.control<ServiceRequestType>('PETICION'),
    asunto: this.fb.control('', [Validators.required, Validators.maxLength(180)]),
    descripcion: this.fb.control('', [Validators.required, Validators.maxLength(4000)]),
  });
  readonly changeForm = this.fb.group({
    campo: this.fb.control('TELEFONO'),
    valor: this.fb.control('', [Validators.required, Validators.maxLength(500)]),
    motivo: this.fb.control('', [Validators.required, Validators.maxLength(500)]),
  });

  constructor() {
    this.companyService.current().pipe(catchError(() => of(null))).subscribe((c) => this.company.set(c));
    this.service.portalSummary().pipe(catchError(() => of(null)), finalize(() => this.loading.set(false))).subscribe((s) => this.summary.set(s));
    if (this.can.contracts) {
      this.service.portalContracts().pipe(catchError(() => of([] as PortalContract[]))).subscribe((list) => {
        this.contracts.set(list);
        if (list.length) this.selectContract(list[0].id);
      });
    }
    if (this.can.payments) {
      this.service.portalPayments().pipe(catchError(() => of([] as PortalPayment[]))).subscribe((list) => this.payments.set(list));
    }
    if (this.can.documents) {
      this.service.portalDocuments(0, 50).pipe(catchError(() => of(null))).subscribe((page) => this.documents.set(page?.contenido ?? []));
    }
    this.loadRequests();
    this.loadChanges();
  }

  go(section: Section): void {
    this.section.set(section);
    this.menuOpen.set(false);
    globalThis.scrollTo?.({ top: 0, behavior: 'smooth' });
  }

  selectContract(id: number | string): void {
    const contractId = Number(id);
    this.contractId.set(contractId);
    if (!this.can.installments) return;
    this.installmentsLoading.set(true);
    this.service
      .portalInstallments(contractId)
      .pipe(catchError(() => of([] as PortalInstallment[])), finalize(() => this.installmentsLoading.set(false)))
      .subscribe((list) => this.installments.set(list));
  }

  loadRequests(): void {
    if (!this.can.requests) return;
    this.service.requests({ size: 30 }).pipe(catchError(() => of(null))).subscribe((page) => this.requests.set(page?.contenido ?? []));
  }

  loadChanges(): void {
    if (!this.can.dataChange) return;
    this.service.dataChanges(0, 30).pipe(catchError(() => of(null))).subscribe((page) => this.changes.set(page?.contenido ?? []));
  }

  download(doc: ClientPortalDocument): void {
    if (!doc.archivoId) return;
    this.service.downloadPortalDocument(doc.archivoId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = doc.nombreArchivo || `${doc.codigo}.pdf`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo descargar el documento.')),
    });
  }

  sendRequest(): void {
    if (this.requestForm.invalid) return this.requestForm.markAllAsTouched();
    const v = this.requestForm.getRawValue();
    const contract = this.contract();
    this.saving.set(true);
    this.service
      .createRequest({
        proyectoId: null,
        clienteId: null,
        contratoId: contract?.id ?? null,
        tipo: v.tipo,
        categoria: 'PORTAL',
        asunto: v.asunto.trim(),
        descripcion: v.descripcion.trim(),
        prioridad: v.tipo === 'RECLAMO' || v.tipo === 'QUEJA' ? 'ALTA' : 'MEDIA',
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (r) => {
          this.requestOpen.set(false);
          this.requestForm.reset({ tipo: 'PETICION', asunto: '', descripcion: '' });
          this.toast.success(`Recibimos su solicitud ${r.codigo}. Le responderemos antes del ${new Date(r.fechaLimite).toLocaleDateString('es-CO')}.`);
          this.loadRequests();
        },
        error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo enviar la solicitud.')),
      });
  }

  sendChange(): void {
    if (this.changeForm.invalid) return this.changeForm.markAllAsTouched();
    const v = this.changeForm.getRawValue();
    this.saving.set(true);
    this.service
      .requestDataChange(v.campo, v.valor.trim(), v.motivo.trim())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.changeOpen.set(false);
          this.changeForm.reset({ campo: 'TELEFONO', valor: '', motivo: '' });
          this.toast.success('Solicitud enviada. La inmobiliaria la revisará y le avisará.');
          this.loadChanges();
        },
        error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo enviar la solicitud de cambio.')),
      });
  }

  cancelChange(change: ClientDataChange): void {
    this.service.cancelDataChange(change.id).subscribe({
      next: () => {
        this.toast.success('Solicitud cancelada.');
        this.loadChanges();
      },
      error: (e) => this.toast.error(apiErrorMessage(e, 'No se pudo cancelar.')),
    });
  }

  fieldLabel(code: string): string {
    return DATA_FIELDS.find((f) => f.value === code)?.label ?? code;
  }

  typeLabel(code: string): string {
    return REQUEST_TYPES.find((t) => t.value === code)?.label ?? code;
  }

  logout(): void {
    const request = this.auth.logoutRequest();
    const done = () => {
      this.auth.clearSession();
      void this.router.navigate(['/login']);
    };
    if (!request) return done();
    request.pipe(finalize(done)).subscribe({ error: () => undefined });
  }
}
