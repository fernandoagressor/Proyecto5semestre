import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CustomerServiceService } from './customer-service.service';

describe('CustomerServiceService', () => {
  let service: CustomerServiceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CustomerServiceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the PQRS tray with server-side filters', () => {
    service.requests({ status: 'EN_PROCESO', type: 'RECLAMO' }).subscribe();
    const request = http.expectOne((item) => item.url === '/api/v1/solicitudes');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('estado')).toBe('EN_PROCESO');
    expect(request.request.params.get('tipo')).toBe('RECLAMO');
    request.flush({ contenido: [] });
  });

  it('sends public and internal messages through the same traced conversation', () => {
    service.sendMessage(17, 'Nota del agente', true).subscribe();
    const request = http.expectOne('/api/v1/solicitudes/17/mensajes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      mensaje: 'Nota del agente',
      interno: true,
      canal: 'INTERNO',
    });
    request.flush({});
  });

  it('uploads a request attachment without using general document permissions', () => {
    const file = new File(['evidencia'], 'evidencia.pdf', { type: 'application/pdf' });
    service.uploadAttachment(17, file, 4).subscribe();
    const request = http.expectOne('/api/v1/solicitudes/17/adjuntos/archivo');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBe(true);
    expect((request.request.body as FormData).get('mensajeId')).toBe('4');
    expect(((request.request.body as FormData).get('archivo') as File).name).toBe('evidencia.pdf');
    request.flush({});
  });

  it('programs an appointment through the service agenda endpoint', () => {
    service.createAppointment({
      proyectoId: 3,
      clienteId: 8,
      solicitudId: 17,
      responsableId: 5,
      tipo: 'VISITA_TECNICA',
      fechaInicio: '2026-08-20T09:00',
      fechaFin: '2026-08-20T10:00',
      lugar: 'Proyecto Central',
    }).subscribe();
    const request = http.expectOne('/api/v1/citas-servicio');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.solicitudId).toBe(17);
    expect(request.request.body.tipo).toBe('VISITA_TECNICA');
    request.flush({});
  });

  it('downloads files only through ownership-checked service routes', () => {
    service.downloadRequestAttachment(17, 90).subscribe();
    const attachment = http.expectOne('/api/v1/solicitudes/17/adjuntos/90/descargar');
    expect(attachment.request.method).toBe('GET');
    attachment.flush(new Blob());

    service.downloadPortalDocument(91).subscribe();
    const portal = http.expectOne('/api/v1/portal/documentos/91/descargar');
    expect(portal.request.method).toBe('GET');
    portal.flush(new Blob());
  });
});
