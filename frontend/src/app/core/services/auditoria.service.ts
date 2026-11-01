import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import { AlertStatus, AnomalyAlert, AuditEvent, AuditEventQuery, AuditExport } from '../models/auditoria.models';

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/auditoria`;

  events(query: AuditEventQuery): Observable<PageResponse<AuditEvent>> {
    let params = new HttpParams().set('pagina', query.pagina ?? 0).set('tamano', query.tamano ?? 50);
    for (const key of ['proyectoId', 'actorId', 'modulo', 'accion', 'desde', 'hasta'] as const) {
      const value = query[key];
      if (value !== null && value !== undefined && value !== '') params = params.set(key, value);
    }
    return this.http.get<PageResponse<AuditEvent>>(`${this.base}/eventos`, { params });
  }

  alerts(estado: AlertStatus | '' = '', severidad = ''): Observable<PageResponse<AnomalyAlert>> {
    let params = new HttpParams().set('tamano', 50);
    if (estado) params = params.set('estado', estado);
    if (severidad) params = params.set('severidad', severidad);
    return this.http.get<PageResponse<AnomalyAlert>>(`${this.base}/alertas`, { params });
  }

  assignAlert(id: number, usuarioId: number): Observable<AnomalyAlert> {
    return this.http.patch<AnomalyAlert>(`${this.base}/alertas/${id}/asignar`, { usuarioId });
  }

  resolveAlert(id: number, resolucion: string, descartar: boolean): Observable<AnomalyAlert> {
    return this.http.patch<AnomalyAlert>(`${this.base}/alertas/${id}/resolver`, { resolucion, descartar });
  }

  /** Deja constancia de la exportación (motivo y filtros); el archivo lo arma el navegador. */
  registerExport(motivo: string, formato: string, filtros: Record<string, unknown>): Observable<AuditExport> {
    return this.http.post<AuditExport>(`${this.base}/exportaciones`, { motivo, formato, filtros });
  }
}
