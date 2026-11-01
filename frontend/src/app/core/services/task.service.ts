import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';

export type TaskStatus = 'PENDIENTE' | 'EN_PROCESO' | 'COMPLETADA' | 'VENCIDA' | 'CANCELADA';
export type TaskPriority = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA';

/** Espejo de dto/gerencia/TareaDtos.Response. */
export interface TeamTask {
  id: number;
  proyectoId: number | null;
  asignadoA: number | null;
  creadoPor: number | null;
  titulo: string;
  descripcion: string | null;
  tipo: string;
  prioridad: TaskPriority;
  estado: TaskStatus;
  fechaVencimiento: string | null;
  entidadTipo: string | null;
  entidadId: number | null;
  reglaOrigen: string | null;
  completadaEn: string | null;
  creadoEn: string;
  version: number;
}

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly http = inject(HttpClient);

  list(q: { estado?: TaskStatus; asignadoA?: number | null; proyectoId?: number | null; tamano?: number } = {}): Observable<PageResponse<TeamTask>> {
    let params = new HttpParams().set('tamano', q.tamano ?? 20);
    if (q.estado) params = params.set('estado', q.estado);
    if (q.asignadoA) params = params.set('asignadoA', q.asignadoA);
    if (q.proyectoId) params = params.set('proyectoId', q.proyectoId);
    return this.http.get<PageResponse<TeamTask>>(`${API_BASE_URL}/tareas`, { params });
  }
}
