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

  markRead(id: number) {
    return this.http.patch<AppNotification>(`${API_BASE_URL}/notificaciones/${id}/leer`, {});
  }
}

/** Pantalla que muestra lo que avisa la notificación, según la entidad a la que apunta. */
export function notificationLink(notification: Pick<AppNotification, 'entidadTipo' | 'entidadId'>): {
  path: string;
  queryParams: Record<string, string | number>;
} | null {
  const id = notification.entidadId;
  switch (notification.entidadTipo) {
    case 'SOLICITUD_PQRS':
      return { path: '/servicio', queryParams: id ? { pestana: 'solicitudes', solicitud: id } : { pestana: 'solicitudes' } };
    case 'CITA_SERVICIO':
      return { path: '/servicio', queryParams: { pestana: 'citas' } };
    case 'TICKET_SOPORTE':
      return { path: '/servicio', queryParams: id ? { pestana: 'tickets', ticket: id } : { pestana: 'tickets' } };
    case 'PROYECTO':
      return { path: '/inventario', queryParams: id ? { proyecto: id } : {} };
    case 'ALERTA_ANOMALIA':
      return { path: '/auditoria', queryParams: id ? { alerta: id } : {} };
    case 'SOLICITUD_CAMBIO_DATOS':
      return { path: '/servicio', queryParams: {} };
    default:
      return null;
  }
}
