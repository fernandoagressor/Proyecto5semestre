import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SalesService } from './sales.service';

describe('SalesService', () => {
  let service: SalesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SalesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a customer through the company-scoped endpoint', () => {
    const payload = {
      tipoPersona: 'NATURAL' as const,
      tipoDocumento: 'CC', numeroDocumento: '10001', nombres: 'Cliente',
      apellidos: 'Prueba', telefono: '3000000000', correo: 'cliente@nexora.test',
      direccion: null, origen: 'VENTAS', observaciones: null,
    };
    service.createCustomer(payload).subscribe();
    const request = http.expectOne('/api/v1/clientes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({});
  });

  it('creates a reservation using the controlled sales payload', () => {
    service.createReservation({ oportunidadId: 8, simulacionId: null, clienteId: 10,
      loteId: 12, fechaVencimiento: '2026-08-15T18:00:00', valorReserva: 500000,
      formaPago: 'TRANSFERENCIA' }).subscribe();
    const request = http.expectOne('/api/v1/reservas');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.loteId).toBe(12);
    expect(request.request.body.clienteId).toBe(10);
    request.flush({});
  });

  it('uploads sales evidence as multipart data bound to a project', () => {
    const file = new File(['evidencia'], 'comprobante.pdf', { type: 'application/pdf' });
    service.uploadDocument(7, file).subscribe();
    const request = http.expectOne((item) => item.url === '/api/v1/ventas/documentos');
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('proyectoId')).toBe('7');
    expect(request.request.body instanceof FormData).toBe(true);
    expect(((request.request.body as FormData).get('archivo') as File).name).toBe('comprobante.pdf');
    request.flush({});
  });

  it('registers a signature only through its contract signer route', () => {
    service.registerSignature(20, 4, { metodo: 'DIGITAL', proveedor: 'Nexora',
      referenciaExterna: 'SIG-1', hashDocumento: 'a'.repeat(64), ipOrigen: null,
      firmadaEn: null, estadoValidacion: 'VALIDA', archivoFirmaId: 99 }).subscribe();
    const request = http.expectOne('/api/v1/contratos/20/firmantes/4/firmas');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.estadoValidacion).toBe('VALIDA');
    expect(request.request.body.hashDocumento).toHaveLength(64);
    request.flush({});
  });
});
