import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  CatalogCompany,
  CatalogContact,
  CatalogImage,
  CatalogProjectDetail,
  CatalogStatus,
} from '../models/catalog.models';

/** Catálogo público (sin sesión) y su administración desde la empresa (publicar, galería). */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  company(code: string): Observable<CatalogCompany> {
    return this.http.get<CatalogCompany>(`${API_BASE_URL}/publico/catalogo/${encodeURIComponent(code)}`);
  }

  project(code: string, projectId: number): Observable<CatalogProjectDetail> {
    return this.http.get<CatalogProjectDetail>(`${API_BASE_URL}/publico/catalogo/${encodeURIComponent(code)}/proyectos/${projectId}`);
  }

  contact(code: string, payload: CatalogContact): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/publico/catalogo/${encodeURIComponent(code)}/contacto`, payload);
  }

  status(): Observable<CatalogStatus> {
    return this.http.get<CatalogStatus>(`${API_BASE_URL}/empresas/actual/catalogo`);
  }

  setPublic(publico: boolean): Observable<CatalogStatus> {
    return this.http.put<CatalogStatus>(`${API_BASE_URL}/empresas/actual/catalogo`, { publico });
  }

  images(projectId: number): Observable<CatalogImage[]> {
    return this.http.get<CatalogImage[]>(`${API_BASE_URL}/proyectos/${projectId}/imagenes`);
  }

  addImage(projectId: number, url: string, titulo: string | null): Observable<CatalogImage> {
    return this.http.post<CatalogImage>(`${API_BASE_URL}/proyectos/${projectId}/imagenes`, { url, titulo });
  }

  removeImage(projectId: number, imageId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/proyectos/${projectId}/imagenes/${imageId}`);
  }
}
