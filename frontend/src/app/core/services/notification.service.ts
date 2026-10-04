import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';

export interface AppNotification {
  id: number;
  tipo: string;
  titulo: string;
  mensaje: string;
  entidadTipo: string | null;
  entidadId: number | null;
  prioridad: string;
  leidaEn: string | null;
  expiraEn: string | null;
  creadoEn: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);

  unreadSummary() {
    return this.http.get<{ noLeidas: number }>(`${API_BASE_URL}/notificaciones/resumen`);
  }

  unreadList() {
    return this.http.get<PageResponse<AppNotification>>(`${API_BASE_URL}/notificaciones`, {
      params: { leida: false, pagina: 0, tamano: 5 },
    });
  }
}
