import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SessionData } from '../models/auth.models';
import { AuthService } from './auth.service';

describe('AuthService password change', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    const session: SessionData = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      accessTokenExpiresAt: Date.now() + 60_000,
      user: {
        id: 7,
        empresaId: 1,
        nombreUsuario: 'fernandoj',
        correo: 'fernando@nexora.test',
        roles: ['ADMINISTRADOR'],
        permisos: ['DASHBOARD_VER', 'DASHBOARD_GERENCIAL_VER'],
        requiereCambioPassword: true,
        mfaHabilitado: false,
      },
    };
    sessionStorage.setItem('nexora_core_session', JSON.stringify(session));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('sends the three password fields to the secured endpoint', () => {
    const payload = {
      passwordActual: 'Actual#2026Segura',
      passwordNueva: 'Nueva#2026Segura',
      confirmacionPassword: 'Nueva#2026Segura',
    };

    service.changePassword(payload).subscribe();

    const request = http.expectOne('/api/v1/auth/cambiar-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(null);
  });
});
