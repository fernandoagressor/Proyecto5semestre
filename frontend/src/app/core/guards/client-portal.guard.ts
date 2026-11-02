import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

const isClient = (auth: AuthService) => auth.user()?.roles.includes('CLIENTE') === true;

/** El portal es solo para clientes; con contraseña temporal, primero se cambia. */
export const clientPortalGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.user()?.requiereCambioPassword) return router.createUrlTree(['/seguridad/cambiar-password']);
  return isClient(auth) ? true : router.createUrlTree(['/dashboard']);
};

/** Un cliente no entra a las pantallas internas: se le lleva a su portal (salvo seguridad de la cuenta). */
export const internalAreaGuard: CanActivateChildFn = (_route, state) => {
  const auth = inject(AuthService);
  if (!isClient(auth) || state.url.startsWith('/seguridad')) return true;
  return inject(Router).createUrlTree(['/portal']);
};
