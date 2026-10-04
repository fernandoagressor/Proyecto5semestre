import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ProjectService } from './project.service';

describe('ProjectService', () => {
  let service: ProjectService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProjectService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('changes a project state through the controlled endpoint', () => {
    service.changeStatus(7, 'ACTIVO').subscribe();

    const request = http.expectOne('/api/v1/proyectos/7/estado');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ estado: 'ACTIVO' });
    request.flush({});
  });
});
