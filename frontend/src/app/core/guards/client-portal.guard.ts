import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

export const CLIENT_PASSWORD_URL = '/portal/cambiar-password';

export const isClient = (auth: AuthService) => auth.user()?.roles.includes('CLIENTE') === true;

/** Pantalla de inicio de cada usuario según su tipo y si debe cambiar la contraseña. */
export function homeUrl(auth: AuthService): string {
  const client = isClient(auth);
  if (auth.user()?.requiereCambioPassword) return client ? CLIENT_PASSWORD_URL : '/seguridad/cambiar-password';
  return client ? '/portal' : '/dashboard';
}

/** El portal es solo para clientes; con contraseña temporal, primero se cambia (en el diseño del portal). */
export const clientPortalGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.user()?.requiereCambioPassword) return router.createUrlTree([CLIENT_PASSWORD_URL]);
  return isClient(auth) ? true : router.createUrlTree(['/dashboard']);
};

/** Cambio de contraseña con el diseño del portal: solo para clientes. */
export const clientAccountGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return isClient(auth) ? true : inject(Router).createUrlTree(['/seguridad/cambiar-password']);
};

/** Un cliente nunca entra al diseño interno (menú del equipo): se le lleva a su portal. */
export const internalAreaGuard: CanActivateChildFn = () => {
  const auth = inject(AuthService);
  if (!isClient(auth)) return true;
  return inject(Router).createUrlTree([homeUrl(auth)]);
};
