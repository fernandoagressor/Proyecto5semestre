import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import {
  Appointment,
  CreateSimulationPayload,
  Opportunity,
  OpportunityLot,
  OpportunityStage,
  Prospect,
  ProspectPayload,
  ProspectStatus,
  ProspectTemperature,
  Simulation,
} from '../models/crm.models';

@Injectable({ providedIn: 'root' })
export class CrmService {
  private readonly http = inject(HttpClient);

  prospects(options: {
    text?: string;
    status?: string;
    temperature?: string;
    projectId?: number | null;
    page?: number;
    size?: number;
  }): Observable<PageResponse<Prospect>> {
    let params = this.pageParams(options.page, options.size);
    if (options.text) params = params.set('texto', options.text);
    if (options.status) params = params.set('estado', options.status);
    if (options.temperature) params = params.set('temperatura', options.temperature);
    if (options.projectId) params = params.set('proyectoId', options.projectId);
    return this.http.get<PageResponse<Prospect>>(`${API_BASE_URL}/prospectos`, { params });
  }

  createProspect(payload: ProspectPayload): Observable<Prospect> {
    return this.http.post<Prospect>(`${API_BASE_URL}/prospectos`, payload);
  }

  qualifyProspect(
    id: number,
    estado: ProspectStatus,
    temperatura: ProspectTemperature,
    justificacion: string,
  ): Observable<Prospect> {
    return this.http.patch<Prospect>(`${API_BASE_URL}/prospectos/${id}/calificacion`, {
      estado,
      temperatura,
      justificacion,
    });
  }

  opportunities(options: {
    text?: string;
    status?: string;
    stage?: string;
    projectId?: number | null;
    page?: number;
    size?: number;
  }): Observable<PageResponse<Opportunity>> {
    let params = this.pageParams(options.page, options.size);
    if (options.text) params = params.set('texto', options.text);
    if (options.status) params = params.set('estado', options.status);
    if (options.stage) params = params.set('etapa', options.stage);
    if (options.projectId) params = params.set('proyectoId', options.projectId);
    return this.http.get<PageResponse<Opportunity>>(`${API_BASE_URL}/oportunidades`, { params });
  }

  createOpportunity(payload: {
    proyectoId: number;
    prospectoId: number;
    asesorId: number | null;
    valorEstimado: number | null;
    fechaProximaAccion: string | null;
  }): Observable<Opportunity> {
    return this.http.post<Opportunity>(`${API_BASE_URL}/oportunidades`, payload);
  }

  opportunityLots(opportunityId: number): Observable<OpportunityLot[]> {
    return this.http.get<OpportunityLot[]>(`${API_BASE_URL}/oportunidades/${opportunityId}/lotes`);
  }

  addOpportunityLot(
    opportunityId: number,
    lotId: number,
    priority: number,
  ): Observable<OpportunityLot> {
    return this.http.post<OpportunityLot>(`${API_BASE_URL}/oportunidades/${opportunityId}/lotes`, {
      loteId: lotId,
      prioridad: priority,
    });
  }

  removeOpportunityLot(opportunityId: number, lotId: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/oportunidades/${opportunityId}/lotes/${lotId}`);
  }

  selectOpportunityLot(
    opportunityId: number,
    lotId: number,
    selected = true,
  ): Observable<OpportunityLot> {
    return this.http.patch<OpportunityLot>(
      `${API_BASE_URL}/oportunidades/${opportunityId}/lotes/${lotId}/seleccion`,
      { seleccionado: selected },
    );
  }

  moveOpportunity(
    id: number,
    etapa: OpportunityStage,
    probabilidad: number,
    observacion: string,
  ): Observable<Opportunity> {
    return this.http.patch<Opportunity>(`${API_BASE_URL}/oportunidades/${id}/etapa`, {
      etapa,
      probabilidad,
      observacion,
    });
  }

  appointments(options: {
    from: string;
    to: string;
    status?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<Appointment>> {
    let params = this.pageParams(options.page, options.size)
      .set('desde', options.from)
      .set('hasta', options.to);
    if (options.status) params = params.set('estado', options.status);
    return this.http.get<PageResponse<Appointment>>(`${API_BASE_URL}/agenda/comercial`, { params });
  }

  createAppointment(
    opportunityId: number,
    payload: { tipo: string; fechaInicio: string; fechaFin: string; lugar: string | null },
  ): Observable<Appointment> {
    return this.http.post<Appointment>(
      `${API_BASE_URL}/oportunidades/${opportunityId}/citas`,
      payload,
    );
  }

  simulations(options: {
    status?: string;
    projectId?: number | null;
    opportunityId?: number | null;
    page?: number;
    size?: number;
  }): Observable<PageResponse<Simulation>> {
    let params = this.pageParams(options.page, options.size);
    if (options.status) params = params.set('estado', options.status);
    if (options.projectId) params = params.set('proyectoId', options.projectId);
    if (options.opportunityId) params = params.set('oportunidadId', options.opportunityId);
    return this.http.get<PageResponse<Simulation>>(`${API_BASE_URL}/simulaciones`, { params });
  }

  createSimulation(payload: CreateSimulationPayload): Observable<Simulation> {
    return this.http.post<Simulation>(`${API_BASE_URL}/simulaciones`, payload);
  }

  sendSimulation(id: number): Observable<Simulation> {
    return this.http.patch<Simulation>(`${API_BASE_URL}/simulaciones/${id}/enviar`, {});
  }

  private pageParams(page = 0, size = 20): HttpParams {
    return new HttpParams().set('pagina', page).set('tamano', size);
  }
}
