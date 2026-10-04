import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SessionData, TokenResponse } from '../models/auth.models';
import { AuthService } from './auth.service';

describe('AuthService company context', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    const session: SessionData = {
      accessToken: 'old-access', refreshToken: 'refresh-token', accessTokenExpiresAt: Date.now() + 60_000,
      user: { id: 1, empresaId: 2, nombreUsuario: 'superadmin', correo: 'root@nex.test', roles: ['SUPER_ADMIN'],
        permisos: ['CONTEXTO_EMPRESA_CAMBIAR'], requiereCambioPassword: false, mfaHabilitado: false },
    };
    sessionStorage.setItem('nexora_core_session', JSON.stringify(session));
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('rotates the session and activates the selected company', () => {
    const response: TokenResponse = {
      tipo: 'Bearer', accessToken: 'new-access', refreshToken: 'new-refresh', expiraEnSegundos: 900,
      usuario: { ...service.user()!, empresaId: 9 },
    };
    service.switchCompany(9).subscribe();
    const request = http.expectOne('/api/v1/auth/contexto');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ empresaId: 9, refreshToken: 'refresh-token' });
    request.flush(response);
    expect(service.user()?.empresaId).toBe(9);
    expect(service.refreshToken()).toBe('new-refresh');
  });
});
