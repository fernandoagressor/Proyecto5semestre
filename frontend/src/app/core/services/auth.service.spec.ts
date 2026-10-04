import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { SessionData } from '../models/auth.models';
import { AuthService } from './auth.service';

describe('AuthService permissions', () => {
  const session: SessionData = {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    accessTokenExpiresAt: Date.now() + 60_000,
    user: {
      id: 1,
      empresaId: 8,
      nombreUsuario: 'gerente',
      correo: 'gerente@nexora.test',
      roles: ['GERENTE'],
      permisos: ['DASHBOARD_VER', 'PROYECTOS_VER'],
      requiereCambioPassword: false,
      mfaHabilitado: false,
    },
  };

  beforeEach(() => {
    sessionStorage.setItem('nexora_core_session', JSON.stringify(session));
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
  });

  afterEach(() => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('resolves permissions and roles independently', () => {
    const service = TestBed.inject(AuthService);
    expect(service.hasAuthority('DASHBOARD_VER')).toBe(true);
    expect(service.hasAuthority('ROLE_GERENTE')).toBe(true);
    expect(service.hasAuthority('AUDITORIA_VER')).toBe(false);
  });

  it('supports any and all permission checks', () => {
    const service = TestBed.inject(AuthService);
    expect(service.hasAny(['AUDITORIA_VER', 'PROYECTOS_VER'])).toBe(true);
    expect(service.hasAll(['DASHBOARD_VER', 'PROYECTOS_VER'])).toBe(true);
    expect(service.hasAll(['DASHBOARD_VER', 'AUDITORIA_VER'])).toBe(false);
  });
});
