import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CrmService } from './crm.service';

describe('CrmService', () => {
  let service: CrmService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CrmService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the commercial agenda with an explicit date window', () => {
    service
      .appointments({ from: '2026-08-01T00:00:00', to: '2026-09-01T00:00:00', size: 100 })
      .subscribe();

    const request = http.expectOne((item) => item.url === '/api/v1/agenda/comercial');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('desde')).toBe('2026-08-01T00:00:00');
    expect(request.request.params.get('hasta')).toBe('2026-09-01T00:00:00');
    expect(request.request.params.get('tamano')).toBe('100');
    request.flush({
      contenido: [],
      pagina: 0,
      tamano: 100,
      totalElementos: 0,
      totalPaginas: 0,
      primera: true,
      ultima: true,
    });
  });

  it('moves opportunities without bypassing the controlled stage endpoint', () => {
    service.moveOpportunity(15, 'VISITA', 55, 'Visita confirmada').subscribe();

    const request = http.expectOne('/api/v1/oportunidades/15/etapa');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({
      etapa: 'VISITA',
      probabilidad: 55,
      observacion: 'Visita confirmada',
    });
    request.flush({});
  });

  it('links a lot to an opportunity with an explicit priority', () => {
    service.addOpportunityLot(15, 42, 2).subscribe();

    const request = http.expectOne('/api/v1/oportunidades/15/lotes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ loteId: 42, prioridad: 2 });
    request.flush({});
  });

  it('selects the preferred lot through the opportunity relation', () => {
    service.selectOpportunityLot(15, 42).subscribe();

    const request = http.expectOne('/api/v1/oportunidades/15/lotes/42/seleccion');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ seleccionado: true });
    request.flush({});
  });

  it('removes only the requested opportunity lot relation', () => {
    service.removeOpportunityLot(15, 42).subscribe();

    const request = http.expectOne('/api/v1/oportunidades/15/lotes/42');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
