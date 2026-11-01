import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../core/services/auth.service';
import { CustomerServiceService } from '../../core/services/customer-service.service';
import { ProjectService } from '../../core/services/project.service';
import { SalesService } from '../../core/services/sales.service';
import { CustomerServicePage } from './customer-service.page';

describe('CustomerServicePage', () => {
  it('loads the operational request and appointment trays', async () => {
    const { fixture, component } = await setup();

    expect(component.requests()).toHaveLength(1);
    expect(component.appointments()).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('PQRS abiertas');
    expect(fixture.nativeElement.textContent).toContain('Problema con escritura');
  });

  it('creates a traced service request with the selected customer', async () => {
    const created = request({ id: 12, codigo: 'PQRS-20260812-NEW', asunto: 'Nueva petición' });
    const createRequest = vi.fn(() => of(created));
    const { component } = await setup({ createRequest });

    component.openRequestForm();
    component.requestForm.setValue({
      customerId: 8,
      projectId: 3,
      type: 'PETICION',
      category: 'Escrituración',
      subject: 'Nueva petición',
      description: 'Necesito una copia actualizada.',
      priority: 'MEDIA',
    });
    component.saveRequest();

    expect(createRequest).toHaveBeenCalledWith(expect.objectContaining({ clienteId: 8, proyectoId: 3 }));
    expect(component.requests()[0].codigo).toBe('PQRS-20260812-NEW');
    expect(component.feedback()).toContain('radicada correctamente');
  });

  it('opens the request conversation, attachments and internal history', async () => {
    const { component } = await setup();

    component.openRequest(component.requests()[0]);

    expect(component.messages()[0].mensaje).toBe('Estamos revisando el caso.');
    expect(component.attachments()[0].nombreArchivo).toBe('soporte.pdf');
    expect(component.history()[0].campo).toBe('estado');
    expect(component.detailView()).toBe('conversacion');
  });

  it('programs a service appointment linked to the request', async () => {
    const createAppointment = vi.fn(() => of(appointment({ id: 23 })));
    const { component } = await setup({ createAppointment });

    component.openAppointmentForm(component.requests()[0]);
    component.appointmentForm.patchValue({
      customerId: 8,
      projectId: 3,
      requestId: 11,
      type: 'VISITA_TECNICA',
      start: '2026-08-20T09:00',
      end: '2026-08-20T10:00',
      place: 'Proyecto Central',
    });
    component.saveAppointment();

    expect(createAppointment).toHaveBeenCalledWith(
      expect.objectContaining({ clienteId: 8, solicitudId: 11, tipo: 'VISITA_TECNICA' }),
    );
    expect(component.appointments().some((item) => item.id === 23)).toBe(true);
  });
});

async function setup(overrides: Record<string, unknown> = {}): Promise<{
  fixture: ComponentFixture<CustomerServicePage>;
  component: CustomerServicePage;
}> {
  const service = {
    requests: vi.fn(() => of(page([request()]))),
    appointments: vi.fn(() => of(page([appointment()]))),
    createRequest: vi.fn(() => of(request())),
    messages: vi.fn(() => of([{ id: 1, usuarioId: 4, mensaje: 'Estamos revisando el caso.', interno: false, canal: 'PORTAL', creadoEn: '2026-08-12T09:00:00' }])),
    attachments: vi.fn(() => of([{ id: 2, mensajeId: null, archivoId: 90, nombreArchivo: 'soporte.pdf', mimeType: 'application/pdf', tamanoBytes: 1200, creadoEn: '2026-08-12T09:10:00' }])),
    history: vi.fn(() => of([{ id: 3, campo: 'estado', valorAnterior: 'ABIERTA', valorNuevo: 'EN_PROCESO', usuarioId: 4, motivo: 'Inicio de atención', creadoEn: '2026-08-12T09:15:00' }])),
    sendMessage: vi.fn(),
    createAppointment: vi.fn(() => of(appointment())),
    ...overrides,
  };
  const authorities = new Set([
    'SOLICITUDES_VER',
    'SOLICITUDES_CREAR',
    'SOLICITUDES_RESPONDER',
    'SOLICITUDES_ASIGNAR',
    'SOLICITUDES_CAMBIAR_ESTADO',
    'SOLICITUDES_CERRAR',
    'CITAS_VER',
    'CITAS_CREAR',
    'CITAS_REPROGRAMAR',
    'CLIENTES_VER',
    'PROYECTOS_VER',
    'DOCUMENTOS_DESCARGAR',
  ]);

  await TestBed.configureTestingModule({
    imports: [CustomerServicePage],
    providers: [
      { provide: CustomerServiceService, useValue: service },
      {
        provide: AuthService,
        useValue: {
          hasAuthority: (authority: string) => authorities.has(authority),
          user: () => ({ id: 4, roles: ['SERVICIO_CLIENTE'], permisos: [...authorities] }),
        },
      },
      { provide: SalesService, useValue: { customers: () => of(page([customer()])) } },
      { provide: ProjectService, useValue: { list: () => of(page([project()])) } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(CustomerServicePage);
  fixture.detectChanges();
  return { fixture, component: fixture.componentInstance };
}

function request(overrides: Record<string, unknown> = {}) {
  return {
    id: 11,
    codigo: 'PQRS-20260812-ABC123',
    proyectoId: 3,
    clienteId: 8,
    contratoId: 5,
    asignadoA: 4,
    tipo: 'RECLAMO',
    categoria: 'Escrituración',
    asunto: 'Problema con escritura',
    descripcion: 'Solicito revisión del documento.',
    prioridad: 'ALTA',
    estado: 'EN_PROCESO',
    fechaLimite: '2026-08-13T09:00:00',
    fechaPrimeraRespuesta: null,
    fechaResolucion: null,
    fechaCierre: null,
    creadoEn: '2026-08-12T08:00:00',
    actualizadoEn: '2026-08-12T09:00:00',
    ...overrides,
  };
}

function appointment(overrides: Record<string, unknown> = {}) {
  return {
    id: 21,
    proyectoId: 3,
    clienteId: 8,
    solicitudId: 11,
    responsableId: 4,
    tipo: 'ASESORIA',
    fechaInicio: '2026-08-20T09:00:00',
    fechaFin: '2026-08-20T10:00:00',
    lugar: 'Sala 2',
    estado: 'PROGRAMADA',
    resultado: null,
    creadoEn: '2026-08-12T08:00:00',
    ...overrides,
  };
}

function customer() {
  return { id: 8, nombreCompleto: 'Ana Torres', numeroDocumento: '12345678' };
}

function project() {
  return { id: 3, nombre: 'Proyecto Central', estado: 'ACTIVO' };
}

function page<T>(content: T[]) {
  return {
    contenido: content,
    pagina: 0,
    tamano: content.length,
    totalElementos: content.length,
    totalPaginas: content.length ? 1 : 0,
    primera: true,
    ultima: true,
  };
}
