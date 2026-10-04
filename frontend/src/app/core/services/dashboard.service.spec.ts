import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from './auth.service';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DashboardService,
        {
          provide: AuthService,
          useValue: { hasAuthority: () => true },
        },
      ],
    });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the selected period and project to transactional dashboards', () => {
    service.load('gerencial', 9, {
      desde: '2026-07-01',
      hasta: '2026-09-16',
    }).subscribe();

    const request = http.expectOne((candidate) =>
      candidate.url.endsWith('/dashboards/gerencial'));
    expect(request.request.params.get('proyectoId')).toBe('9');
    expect(request.request.params.get('desde')).toBe('2026-07-01');
    expect(request.request.params.get('hasta')).toBe('2026-09-16');
    request.flush({});
  });

  it('uses the end date as the cartera cut-off date', () => {
    service.load('cartera', null, {
      desde: '2026-07-01',
      hasta: '2026-09-16',
    }).subscribe();

    const request = http.expectOne((candidate) =>
      candidate.url.endsWith('/dashboards/cartera'));
    expect(request.request.params.get('corte')).toBe('2026-09-16');
    expect(request.request.params.has('desde')).toBe(false);
    expect(request.request.params.has('hasta')).toBe(false);
    request.flush({});
  });
});
