import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PortfolioService } from './portfolio.service';

describe('PortfolioService', () => {
  let service: PortfolioService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PortfolioService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the operational portfolio dashboard', () => {
    service.summary().subscribe();
    const summary = http.expectOne('/api/v1/cartera/resumen');
    expect(summary.request.method).toBe('GET');
    summary.flush({});

    service.contracts().subscribe();
    const contracts = http.expectOne('/api/v1/cartera/contratos');
    expect(contracts.request.method).toBe('GET');
    contracts.flush([]);
  });

  it('synchronizes an existing signed contract idempotently', () => {
    service.synchronizePlan(14).subscribe();
    const request = http.expectOne('/api/v1/cartera/planes-pago/contratos/14/sincronizar');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({});
  });

  it('registers and confirms a payment with explicit installment applications', () => {
    service.registerPayment({
      contratoId: 4, reservaId: null, clienteId: 8, metodoPagoId: 1,
      fechaPago: '2026-08-11T08:00', valor: 250000, referencia: 'TRX-1',
      comprobanteId: 90, origen: 'MANUAL', observaciones: null,
    }).subscribe();
    const create = http.expectOne('/api/v1/pagos');
    expect(create.request.method).toBe('POST');
    expect(create.request.body.contratoId).toBe(4);
    create.flush({ id: 20 });

    service.confirmPayment(20, [
      { id: 0, cuotaId: 41, facturaId: null, concepto: 'CAPITAL', valor: 250000 },
    ], 'Confirmado').subscribe();
    const confirm = http.expectOne('/api/v1/pagos/20/confirmar');
    expect(confirm.request.method).toBe('PATCH');
    expect(confirm.request.body.aplicaciones).toEqual([
      { cuotaId: 41, facturaId: null, concepto: 'CAPITAL', valor: 250000 },
    ]);
    confirm.flush({});
  });

  it('loads pending, partial and overdue installments for collection follow-up', () => {
    service.followUpInstallments().subscribe();
    const request = http.expectOne(
      (candidate) => candidate.url === '/api/v1/cartera/seguimiento'
        && candidate.params.get('pagina') === '0'
        && candidate.params.get('tamano') === '100',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ contenido: [] });
  });

  it('records preventive collection management against an installment with balance', () => {
    service.createCollection({
      contratoId: 7, cuotaId: 70, canal: 'LLAMADA', resultado: 'Contactado',
      detalle: 'Promete pago', promesaPagoFecha: '2026-08-15', promesaPagoValor: 100000,
    }).subscribe();
    const request = http.expectOne('/api/v1/cartera/gestiones-cobro');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.cuotaId).toBe(70);
    expect(request.request.body.promesaPagoValor).toBe(100000);
    request.flush({});
  });

  it('updates automatic plan configuration using decimal rates', () => {
    service.updateConfiguration({
      tasaEfectivaAnual: 0.12,
      tasaMoraEfectivaAnual: 0.25,
      periodicidad: 'MENSUAL',
      diasPrimeraCuota: 30,
      autoGenerarPlan: true,
    }).subscribe();
    const request = http.expectOne('/api/v1/cartera/configuracion');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.tasaEfectivaAnual).toBe(0.12);
    expect(request.request.body.autoGenerarPlan).toBe(true);
    request.flush({});
  });
});
