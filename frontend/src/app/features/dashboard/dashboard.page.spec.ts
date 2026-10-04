import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';

import {
  DashboardData,
  DashboardDateRange,
  DashboardKind,
} from '../../core/models/dashboard.models';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { ProjectService } from '../../core/services/project.service';
import { DashboardPage, dashboardDateRange } from './dashboard.page';

describe('DashboardPage', () => {
  it('integrates only the dashboards authorized for the current company', () => {
    const load = vi.fn((kind: DashboardKind) => of(dashboards[kind]));
    const component = createDashboardPage(load, new Set([
      'DASHBOARD_GERENCIAL_VER',
      'DASHBOARD_COMERCIAL_VER',
      'DASHBOARD_CARTERA_VER',
    ]));

    expect(component.availableViews().map((view) => view.kind)).toEqual([
      'gerencial', 'comercial', 'cartera',
    ]);
    expect(load).toHaveBeenCalledWith('gerencial', null);
    expect(load).toHaveBeenCalledWith('comercial', null);
    expect(load).toHaveBeenCalledWith('cartera', null);
    expect(load).not.toHaveBeenCalledWith('servicio', null);
    expect(component.operationsSeries().some((item) => item.categoria === 'Contratos')).toBe(true);
  });

  it('keeps the selected project in every authorized enterprise query', () => {
    const load = vi.fn((kind: DashboardKind) => of(dashboards[kind]));
    const component = createDashboardPage(load, new Set([
      'DASHBOARD_GERENCIAL_VER',
      'DASHBOARD_COMERCIAL_VER',
    ]));
    load.mockClear();

    component.selectProject('9');

    expect(load).toHaveBeenCalledWith('gerencial', 9);
    expect(load).toHaveBeenCalledWith('comercial', 9);
    expect(component.selectedProjectId()).toBe(9);
  });

  it('changes between already loaded views without crossing permission boundaries', () => {
    const load = vi.fn((kind: DashboardKind) => of(dashboards[kind]));
    const component = createDashboardPage(load, new Set([
      'DASHBOARD_GERENCIAL_VER',
      'DASHBOARD_COMERCIAL_VER',
    ]));
    load.mockClear();

    component.selectKind('comercial');
    component.selectKind('servicio');

    expect(component.kind()).toBe('comercial');
    expect(load).not.toHaveBeenCalled();
    expect(component.distributionSeries().some((item) => item.categoria === 'Ganadas')).toBe(true);
  });

  it('keeps historical data by default and builds explicit calendar ranges', () => {
    const today = new Date(2026, 8, 16);

    expect(dashboardDateRange('history', today)).toBeUndefined();
    expect(dashboardDateRange('month', today)).toEqual({
      desde: '2026-09-01',
      hasta: '2026-09-16',
    });
    expect(dashboardDateRange('quarter', today)).toEqual({
      desde: '2026-07-01',
      hasta: '2026-09-16',
    });
    expect(dashboardDateRange('year', today)).toEqual({
      desde: '2026-01-01',
      hasta: '2026-09-16',
    });
  });
});

function createDashboardPage(
  load: (
    kind: DashboardKind,
    projectId?: number | null,
    range?: DashboardDateRange,
  ) => Observable<DashboardData>,
  permissions: Set<string>,
): DashboardPage {
  TestBed.configureTestingModule({
    providers: [
      {
        provide: DashboardService,
        useValue: { availableKind: () => 'gerencial', load },
      },
      {
        provide: ProjectService,
        useValue: { list: () => of({ contenido: [] }) },
      },
      {
        provide: AuthService,
        useValue: {
          hasAuthority: (permission: string) => permissions.has(permission),
          hasAny: (values: string[]) => values.some((value) => permissions.has(value)),
          hasAll: (values: string[]) => values.every((value) => permissions.has(value)),
          user: () => ({ empresaId: 7, nombreUsuario: 'admin.empresa' }),
        },
      },
    ],
  });
  return TestBed.runInInjectionContext(() => new DashboardPage());
}

const dashboards: Record<DashboardKind, DashboardData> = {
  gerencial: {
    desde: '2026-08-01', hasta: '2026-08-21', proyectoId: null,
    ventas: 110000000, contratos: 2, recaudo: 30000000, carteraTotal: 80000000,
    carteraVencida: 5000000, indiceMora: 6.25, costos: 25000000, utilidad: 85000000,
    margen: 77.27, lotesDisponibles: 8, inventarioDisponible: 360000000,
    oportunidadesAbiertas: 5, tareasVencidas: 1, pqrsAbiertas: 2,
  },
  comercial: {
    desde: '2026-08-01', hasta: '2026-08-21', proyectoId: null,
    oportunidadesNuevas: 7, oportunidadesGanadas: 2, oportunidadesPerdidas: 1,
    valorEmbudo: 90000000, tasaConversion: 66.67, reservasActivas: 3,
    embudo: [{ categoria: 'NEGOCIACION', cantidad: 3, valor: 90000000 }],
    ventasPorAsesor: [{ categoria: 'Ana', cantidad: 2, valor: 110000000 }],
  },
  cartera: {
    corte: '2026-08-21', proyectoId: null, saldoTotal: 80000000,
    saldoVencido: 5000000, recaudoMes: 30000000, indiceMora: 6.25,
    cuotasVencidas: 2, promesasPendientes: 1,
    edadesMora: [{ categoria: '1-30', cantidad: 2, valor: 5000000 }],
  },
  servicio: {
    desde: '2026-08-01', hasta: '2026-08-21', proyectoId: null,
    solicitudesAbiertas: 2, solicitudesVencidas: 1, horasPrimeraRespuesta: 3,
    satisfaccionPromedio: 4.5, citasPendientes: 2, ticketsAbiertos: 1,
    solicitudesPorTipo: [{ categoria: 'PETICION', cantidad: 2, valor: 2 }],
  },
  cliente: {
    clienteId: 1, codigoCliente: 'CL-001', contratosVigentes: 1,
    cuotasPendientes: 3, saldoPendiente: 12000000, documentosDisponibles: 2,
    solicitudesAbiertas: 0, proximaCita: null,
  },
};
