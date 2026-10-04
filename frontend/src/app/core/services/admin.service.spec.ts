import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdminCompany, CompanyCreateInput } from '../models/admin.models';
import { AdminService } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads consolidated analytics from the protected global endpoint', () => {
    service.analytics().subscribe();

    const request = http.expectOne('/api/v1/admin/analitica');
    expect(request.request.method).toBe('GET');
    request.flush({ empresas: [], lotesPorEstado: [], proyectosPorEstado: [], ventasMensuales: [], recaudoMensual: [] });
  });

  it('lists companies with global filters', () => {
    service.companies({ text: 'nexora', status: 'ACTIVA', page: 1, size: 25 }).subscribe();
    const request = http.expectOne((item) =>
      item.url === '/api/v1/admin/empresas' &&
      item.params.get('texto') === 'nexora' &&
      item.params.get('estado') === 'ACTIVA' &&
      item.params.get('pagina') === '1' &&
      item.params.get('tamano') === '25',
    );
    expect(request.request.method).toBe('GET');
    request.flush({ contenido: [], pagina: 1, tamano: 25, totalElementos: 0, totalPaginas: 0, primera: false, ultima: true });
  });

  it('creates a company and its initial administrator together', () => {
    const payload = {
      codigo: 'NEX', nombre: 'Nexora', nit: '9001', direccion: null, ciudad: 'Medellín',
      departamento: 'Antioquia', telefono: null, correo: 'admin@nex.test', logoUrl: null,
      zonaHoraria: 'America/Bogota', moneda: 'COP', limiteProyectos: 5, limiteUsuarios: 20,
      modulos: ['ADMINISTRACION_SEGURIDAD', 'PROYECTOS_INVENTARIO'],
      administrador: { tipoDocumento: 'CC', numeroDocumento: '1', nombres: 'Admin', apellidos: null,
        nombreUsuario: 'admin.nex', correo: 'admin@nex.test', passwordTemporal: 'Temporal.2026' },
    } satisfies CompanyCreateInput;
    service.createCompany(payload).subscribe();
    const request = http.expectOne('/api/v1/admin/empresas');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.administrador.nombreUsuario).toBe('admin.nex');
    request.flush({ id: 1 } as AdminCompany);
  });

  it('sends the optimistic version when suspending a company', () => {
    const company = { id: 8, nombre: 'Nexora', estado: 'ACTIVA', version: 4 } as AdminCompany;
    service.changeCompanyStatus(company, 'SUSPENDIDA', 'Control administrativo').subscribe();
    const request = http.expectOne('/api/v1/admin/empresas/8/estado');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ estado: 'SUSPENDIDA', motivo: 'Control administrativo', version: 4 });
    request.flush({ ...company, estado: 'SUSPENDIDA', version: 5 });
  });
});
