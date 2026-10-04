import { NAVIGATION_ITEMS } from './navigation.config';

describe('Nexora navigation', () => {
  it('uses unique routes and permission-protected items', () => {
    const routes = NAVIGATION_ITEMS.map((item) => item.route);
    expect(new Set(routes).size).toBe(routes.length);
    expect(NAVIGATION_ITEMS.every((item) => item.permissions.length > 0)).toBe(true);
  });

  it('keeps the dashboard as the first operational entry', () => {
    expect(NAVIGATION_ITEMS[0].route).toBe('/dashboard');
    expect(NAVIGATION_ITEMS[0].permissions).toContain('DASHBOARD_VER');
  });

  it('protects the global console with the SUPER_ADMIN role', () => {
    const admin = NAVIGATION_ITEMS.find((item) => item.route === '/superadmin');
    expect(admin?.permissions).toEqual(['ROLE_SUPER_ADMIN']);
  });
});
