import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import { Project } from '../models/empresa.models';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);

  list(
    options: { text?: string; status?: string; page?: number; size?: number } = {},
  ): Observable<PageResponse<Project>> {
    let params = new HttpParams()
      .set('pagina', options.page ?? 0)
      .set('tamano', options.size ?? 20);
    if (options.text) params = params.set('texto', options.text);
    if (options.status) params = params.set('estado', options.status);
    return this.http.get<PageResponse<Project>>(`${API_BASE_URL}/proyectos`, { params });
  }

  changeStatus(projectId: number, status: Project['estado']): Observable<Project> {
    return this.http.patch<Project>(`${API_BASE_URL}/proyectos/${projectId}/estado`, {
      estado: status,
    });
  }
}
