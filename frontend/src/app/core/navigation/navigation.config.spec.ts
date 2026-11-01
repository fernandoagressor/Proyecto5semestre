import { Route } from '@angular/router';

import { routes } from '../../app.routes';
import { NAVIGATION_GROUPS, NAVIGATION_ITEMS } from './navigation.config';

function findRoute(path: string, list: Route[] = routes): Route | undefined {
  for (const route of list) {
    if (route.path === path) return route;
    const child = route.children && findRoute(path, route.children);
    if (child) return child;
  }
  return undefined;
}

describe('Nexora navigation', () => {
  it('uses unique routes and permission-protected items', () => {
    const paths = NAVIGATION_ITEMS.map((item) => item.route);
    expect(new Set(paths).size).toBe(paths.length);
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

  it('places every item in a known sidebar group', () => {
    const groups = new Set(NAVIGATION_GROUPS.map((group) => group.id));
    expect(NAVIGATION_ITEMS.every((item) => groups.has(item.group))).toBe(true);
  });

  it('only shows a link when its route guard would let the user in', () => {
    for (const item of NAVIGATION_ITEMS) {
      const route = findRoute(item.route.slice(1));
      expect(route).withContext(item.route).toBeDefined();
      expect([...(route?.data?.['permissions'] ?? [])].sort()).withContext(item.route).toEqual([...item.permissions].sort());
      expect(route?.data?.['permissionMode'] ?? 'any').withContext(item.route).toBe(item.permissionMode ?? 'any');
      expect(route?.data?.['module']).withContext(item.route).toBe(item.module);
    }
  });
});
