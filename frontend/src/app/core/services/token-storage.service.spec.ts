import { TokenStorageService } from './token-storage.service';

describe('TokenStorageService', () => {
  const service = new TokenStorageService();

  beforeEach(() => sessionStorage.clear());
  afterEach(() => sessionStorage.clear());

  it('persists and restores the active session', () => {
    service.write({
      tipo: 'Bearer',
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      expiraEnSegundos: 900,
      usuario: {
        id: 1,
        empresaId: 8,
        nombreUsuario: 'superadmin',
        correo: 'admin@nexora.test',
        roles: ['SUPER_ADMIN'],
        permisos: ['DASHBOARD_VER'],
        requiereCambioPassword: false,
        mfaHabilitado: false,
      },
    });

    const session = service.read();
    expect(session?.accessToken).toBe('access-token');
    expect(session?.user.roles).toContain('SUPER_ADMIN');
    expect(session?.accessTokenExpiresAt).toBeGreaterThan(Date.now());
  });

  it('discards malformed data', () => {
    sessionStorage.setItem('nexora_core_session', '{invalid');
    expect(service.read()).toBeNull();
  });
});
