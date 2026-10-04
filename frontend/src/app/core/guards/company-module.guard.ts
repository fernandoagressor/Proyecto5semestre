import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { CompanyModuleCode } from '../models/company-module.models';
import { AuthService } from '../services/auth.service';
import { CompanyService } from '../services/company.service';

export const companyModuleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const companyService = inject(CompanyService);
  const router = inject(Router);
  const module = route.data?.['module'] as CompanyModuleCode | undefined;

  if (!module || !auth.user()?.empresaId) return true;

  return companyService.current().pipe(
    map((company) =>
      company.modulos?.includes(module) ? true : router.createUrlTree(['/sin-acceso']),
    ),
    catchError(() => of(router.createUrlTree(['/sin-acceso']))),
  );
};
