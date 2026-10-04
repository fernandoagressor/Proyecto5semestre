import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Company } from '../models/empresa.models';

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);

  current(): Observable<Company> {
    return this.http.get<Company>(`${API_BASE_URL}/empresas/actual`);
  }
}
