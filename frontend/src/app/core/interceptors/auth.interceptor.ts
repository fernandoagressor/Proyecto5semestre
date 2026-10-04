import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

const PUBLIC_ENDPOINTS = ['/auth/login', '/auth/refresh'];
const NO_REFRESH_ENDPOINTS = [...PUBLIC_ENDPOINTS, '/auth/logout'];

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.accessToken();
  const isPublic = PUBLIC_ENDPOINTS.some((endpoint) => request.url.includes(endpoint));
  const canRefresh = !NO_REFRESH_ENDPOINTS.some((endpoint) => request.url.includes(endpoint));
  const authorizedRequest = token && !isPublic
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authorizedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || !canRefresh || !auth.refreshToken()) {
        return throwError(() => error);
      }

      return auth.refreshSession().pipe(
        switchMap(() => {
          const renewedToken = auth.accessToken();
          return next(
            renewedToken
              ? request.clone({ setHeaders: { Authorization: `Bearer ${renewedToken}` } })
              : request,
          );
        }),
        catchError((refreshError) => {
          auth.clearSession();
          void router.navigate(['/login'], { queryParams: { sesion: 'expirada' } });
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
