import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import {
  ClientDataChange,
  ClientPortalDocument,
  ClientPortalSummary,
  CreateServiceAppointmentPayload,
  CreateServiceRequestPayload,
  ServiceAppointment,
  ServiceAppointmentStatus,
  ServiceAttachment,
  ServiceHistory,
  ServiceMessage,
  ServiceRequest,
  ServiceRequestStatus,
  ServiceRequestType,
} from '../models/service.models';

@Injectable({ providedIn: 'root' })
export class CustomerServiceService {
  private readonly http = inject(HttpClient);

  requests(options: {
    status?: ServiceRequestStatus | '';
    type?: ServiceRequestType | '';
    customerId?: number | null;
    assignedTo?: number | null;
    page?: number;
    size?: number;
  } = {}): Observable<PageResponse<ServiceRequest>> {
    let params = this.pageParams(options.page, options.size);
    if (options.status) params = params.set('estado', options.status);
    if (options.type) params = params.set('tipo', options.type);
    if (options.customerId) params = params.set('clienteId', options.customerId);
    if (options.assignedTo) params = params.set('asignadoA', options.assignedTo);
    return this.http.get<PageResponse<ServiceRequest>>(`${API_BASE_URL}/solicitudes`, { params });
  }

  createRequest(payload: CreateServiceRequestPayload): Observable<ServiceRequest> {
    return this.http.post<ServiceRequest>(`${API_BASE_URL}/solicitudes`, payload);
  }

  request(id: number): Observable<ServiceRequest> {
    return this.http.get<ServiceRequest>(`${API_BASE_URL}/solicitudes/${id}`);
  }

  messages(id: number): Observable<ServiceMessage[]> {
    return this.http.get<ServiceMessage[]>(`${API_BASE_URL}/solicitudes/${id}/mensajes`);
  }

  sendMessage(id: number, message: string, internal: boolean): Observable<ServiceMessage> {
    return this.http.post<ServiceMessage>(`${API_BASE_URL}/solicitudes/${id}/mensajes`, {
      mensaje: message,
      interno: internal,
      canal: internal ? 'INTERNO' : 'PORTAL',
    });
  }

  changeRequestStatus(
    id: number,
    status: ServiceRequestStatus,
    reason: string | null,
  ): Observable<ServiceRequest> {
    return this.http.patch<ServiceRequest>(`${API_BASE_URL}/solicitudes/${id}/estado`, {
      estado: status,
      motivo: reason,
    });
  }

  closeRequest(id: number, reason: string | null): Observable<ServiceRequest> {
    return this.http.patch<ServiceRequest>(`${API_BASE_URL}/solicitudes/${id}/cerrar`, {
      motivo: reason,
    });
  }

  attachments(id: number): Observable<ServiceAttachment[]> {
    return this.http.get<ServiceAttachment[]>(`${API_BASE_URL}/solicitudes/${id}/adjuntos`);
  }

  uploadAttachment(id: number, file: File, messageId?: number | null): Observable<ServiceAttachment> {
    const body = new FormData();
    body.append('archivo', file, file.name);
    if (messageId) body.append('mensajeId', String(messageId));
    return this.http.post<ServiceAttachment>(
      `${API_BASE_URL}/solicitudes/${id}/adjuntos/archivo`,
      body,
    );
  }

  history(id: number): Observable<ServiceHistory[]> {
    return this.http.get<ServiceHistory[]>(`${API_BASE_URL}/solicitudes/${id}/historial`);
  }

  appointments(options: {
    from: string;
    to: string;
    status?: ServiceAppointmentStatus | '';
    responsibleId?: number | null;
    page?: number;
    size?: number;
  }): Observable<PageResponse<ServiceAppointment>> {
    let params = this.pageParams(options.page, options.size)
      .set('desde', options.from)
      .set('hasta', options.to);
    if (options.status) params = params.set('estado', options.status);
    if (options.responsibleId) params = params.set('responsableId', options.responsibleId);
    return this.http.get<PageResponse<ServiceAppointment>>(`${API_BASE_URL}/citas-servicio`, {
      params,
    });
  }

  createAppointment(payload: CreateServiceAppointmentPayload): Observable<ServiceAppointment> {
    return this.http.post<ServiceAppointment>(`${API_BASE_URL}/citas-servicio`, payload);
  }

  rescheduleAppointment(
    id: number,
    start: string,
    end: string,
    place: string | null,
  ): Observable<ServiceAppointment> {
    return this.http.patch<ServiceAppointment>(`${API_BASE_URL}/citas-servicio/${id}/reprogramar`, {
      fechaInicio: start,
      fechaFin: end,
      lugar: place,
    });
  }

  changeAppointmentStatus(
    id: number,
    status: ServiceAppointmentStatus,
    result: string | null,
  ): Observable<ServiceAppointment> {
    return this.http.patch<ServiceAppointment>(`${API_BASE_URL}/citas-servicio/${id}/estado`, {
      estado: status,
      resultado: result,
    });
  }

  portalSummary(): Observable<ClientPortalSummary> {
    return this.http.get<ClientPortalSummary>(`${API_BASE_URL}/portal/resumen`);
  }

  portalDocuments(page = 0, size = 50): Observable<PageResponse<ClientPortalDocument>> {
    return this.http.get<PageResponse<ClientPortalDocument>>(`${API_BASE_URL}/portal/documentos`, {
      params: this.pageParams(page, size),
    });
  }

  dataChanges(page = 0, size = 50): Observable<PageResponse<ClientDataChange>> {
    return this.http.get<PageResponse<ClientDataChange>>(`${API_BASE_URL}/portal/cambios-datos`, {
      params: this.pageParams(page, size),
    });
  }

  requestDataChange(field: string, value: string, reason: string): Observable<ClientDataChange> {
    return this.http.post<ClientDataChange>(`${API_BASE_URL}/portal/cambios-datos`, {
      campo: field,
      valorSolicitado: value,
      motivo: reason,
    });
  }

  cancelDataChange(id: number): Observable<ClientDataChange> {
    return this.http.patch<ClientDataChange>(`${API_BASE_URL}/portal/cambios-datos/${id}/cancelar`, {});
  }

  downloadRequestAttachment(requestId: number, fileId: number): Observable<Blob> {
    return this.http.get(
      `${API_BASE_URL}/solicitudes/${requestId}/adjuntos/${fileId}/descargar`,
      { responseType: 'blob' },
    );
  }

  downloadPortalDocument(fileId: number): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/portal/documentos/${fileId}/descargar`, {
      responseType: 'blob',
    });
  }

  private pageParams(page = 0, size = 100): HttpParams {
    return new HttpParams().set('pagina', page).set('tamano', size);
  }
}
