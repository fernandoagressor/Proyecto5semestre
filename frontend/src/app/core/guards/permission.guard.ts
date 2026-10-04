import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const permissions = (route.data?.['permissions'] as string[] | undefined) ?? [];
  const mode = route.data?.['permissionMode'] === 'all' ? 'all' : 'any';
  const allowed = mode === 'all' ? auth.hasAll(permissions) : auth.hasAny(permissions);
  return allowed ? true : router.createUrlTree(['/sin-acceso']);
};
