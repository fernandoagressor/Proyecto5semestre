import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { SessionData } from '../models/auth.models';
import { passwordChangeGuard } from './password-change.guard';

describe('passwordChangeGuard', () => {
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
        permisos: ['DASHBOARD_VER'],
        requiereCambioPassword: true,
        mfaHabilitado: false,
      },
    };
    sessionStorage.setItem('nexora_core_session', JSON.stringify(session));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([])],
    });
  });

  afterEach(() => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  it('redirects protected modules to the required password change', () => {
    const result = TestBed.runInInjectionContext(() =>
      passwordChangeGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/dashboard' } as RouterStateSnapshot,
      ),
    );

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree))
      .toBe('/seguridad/cambiar-password');
  });

  it('allows access to the password change page itself', () => {
    const result = TestBed.runInInjectionContext(() =>
      passwordChangeGuard(
        {} as ActivatedRouteSnapshot,
        { url: '/seguridad/cambiar-password' } as RouterStateSnapshot,
      ),
    );

    expect(result).toBe(true);
  });
});
