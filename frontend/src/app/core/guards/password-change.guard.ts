import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

const CHANGE_PASSWORD_URL = '/seguridad/cambiar-password';

export const passwordChangeGuard: CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const requiresChange = auth.user()?.requiereCambioPassword === true;

  return !requiresChange || state.url.startsWith(CHANGE_PASSWORD_URL)
    ? true
    : router.createUrlTree([CHANGE_PASSWORD_URL]);
};
