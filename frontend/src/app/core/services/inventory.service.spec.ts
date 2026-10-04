import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { InventoryService } from './inventory.service';

describe('InventoryService', () => {
  let service: InventoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(InventoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends lot filters using the backend contract', () => {
    service.listLots({ projectId: 8, state: 'DISPONIBLE', stageId: 3, blockId: 6, text: 'A-01', minArea: 90, maxArea: 140, minPrice: 80_000_000, maxPrice: 150_000_000, page: 2, size: 20 }).subscribe();

    const request = http.expectOne((item) => item.url === '/api/v1/proyectos/8/lotes');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('estado')).toBe('DISPONIBLE');
    expect(request.request.params.get('etapaId')).toBe('3');
    expect(request.request.params.get('manzanaId')).toBe('6');
    expect(request.request.params.get('texto')).toBe('A-01');
    expect(request.request.params.get('areaMinima')).toBe('90');
    expect(request.request.params.get('areaMaxima')).toBe('140');
    expect(request.request.params.get('valorMinimo')).toBe('80000000');
    expect(request.request.params.get('valorMaximo')).toBe('150000000');
    expect(request.request.params.get('pagina')).toBe('2');
    request.flush({ contenido: [], pagina: 2, tamano: 20, totalElementos: 0, totalPaginas: 0, primera: false, ultima: true });
  });

  it('keeps price changes on the traceable patch endpoint', () => {
    service.changeLotPrice(8, 42, 180_000_000, 'Actualización de lista').subscribe();

    const request = http.expectOne('/api/v1/proyectos/8/lotes/42/precio');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ valor: 180_000_000, motivo: 'Actualización de lista' });
    request.flush({});
  });

  it('updates the editable physical details of an existing lot', () => {
    service.updateLot(8, 42, {
      codigo: 'LT-042',
      numero: '042',
      areaM2: 133,
      frenteM: 9.5,
      fondoM: 14,
      ubicacionReferencia: 'Esquina norte',
      descripcion: 'Dimensiones actualizadas',
    }).subscribe();

    const request = http.expectOne('/api/v1/proyectos/8/lotes/42');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.areaM2).toBe(133);
    expect(request.request.body.frenteM).toBe(9.5);
    request.flush({});
  });
});
