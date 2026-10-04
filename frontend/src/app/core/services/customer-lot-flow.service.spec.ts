import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CustomerLotFlowService } from './customer-lot-flow.service';

describe('CustomerLotFlowService', () => {
  let service: CustomerLotFlowService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CustomerLotFlowService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the complete operation to the transactional endpoint', () => {
    const payload = {
      cliente: {
        tipoPersona: 'NATURAL' as const,
        tipoDocumento: 'CC',
        numeroDocumento: '100200300',
        nombres: 'Ana',
        apellidos: 'Pérez',
        telefono: '3001234567',
        correo: null,
        direccion: null,
        origen: 'VENTAS',
        observaciones: null,
      },
      proyectoId: 8,
      loteId: 42,
      fechaVencimiento: '2026-09-01T17:00',
      valorReserva: 500_000,
      formaPago: 'TRANSFERENCIA',
    };

    service.create(payload).subscribe();

    const request = http.expectOne('/api/v1/ventas/cliente-lote');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({});
  });
});
