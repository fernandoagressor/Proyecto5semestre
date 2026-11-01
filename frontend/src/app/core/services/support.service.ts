import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';

export type TicketStatus = 'ABIERTO' | 'EN_PROCESO' | 'EN_ESPERA' | 'RESUELTO' | 'CERRADO' | 'REABIERTO';
export type ServicePriority = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA';

/** Espejo de dto/soporte/SoporteDtos.Response. */
export interface SupportTicket {
  id: number;
  proyectoId: number | null;
  codigo: string;
  solicitanteId: number | null;
  asignadoA: number | null;
  asunto: string;
  categoria: string;
  prioridad: ServicePriority;
  descripcion: string;
  estado: TicketStatus;
  fechaPrimeraRespuesta: string | null;
  fechaResolucion: string | null;
  fechaCierre: string | null;
  creadoEn: string;
}

export interface TicketMessage {
  id: number;
  usuarioId: number | null;
  mensaje: string;
  interno: boolean;
  creadoEn: string;
}

export interface KnowledgeCategory {
  id: number;
  nombre: string;
  descripcion: string | null;
  orden: number;
}

export interface KnowledgeArticle {
  id: number;
  categoriaId: number;
  titulo: string;
  slug: string | null;
  contenido: string;
  visibleClientes: boolean;
  estado: string;
  autorId: number | null;
  publicadoEn: string | null;
  actualizadoEn: string | null;
}

/** Tickets de soporte interno y base de conocimiento. */
@Injectable({ providedIn: 'root' })
export class SupportService {
  private readonly http = inject(HttpClient);
  private readonly tickets = `${API_BASE_URL}/soporte/tickets`;
  private readonly kb = `${API_BASE_URL}/base-conocimiento`;

  list(q: { estado?: TicketStatus | ''; asignadoA?: number | null; pagina?: number } = {}): Observable<PageResponse<SupportTicket>> {
    let params = new HttpParams().set('pagina', q.pagina ?? 0).set('tamano', 50);
    if (q.estado) params = params.set('estado', q.estado);
    if (q.asignadoA) params = params.set('asignadoA', q.asignadoA);
    return this.http.get<PageResponse<SupportTicket>>(this.tickets, { params });
  }

  create(payload: { proyectoId: number | null; asunto: string; categoria: string; prioridad: ServicePriority; descripcion: string }): Observable<SupportTicket> {
    return this.http.post<SupportTicket>(this.tickets, payload);
  }

  messages(id: number): Observable<TicketMessage[]> {
    return this.http.get<TicketMessage[]>(`${this.tickets}/${id}/mensajes`);
  }

  reply(id: number, mensaje: string, interno: boolean): Observable<TicketMessage> {
    return this.http.post<TicketMessage>(`${this.tickets}/${id}/mensajes`, { mensaje, interno });
  }

  assign(id: number, usuarioId: number, motivo: string | null = null): Observable<SupportTicket> {
    return this.http.patch<SupportTicket>(`${this.tickets}/${id}/asignacion`, { usuarioId, motivo });
  }

  changeStatus(id: number, estado: TicketStatus, motivo: string | null = null): Observable<SupportTicket> {
    return this.http.patch<SupportTicket>(`${this.tickets}/${id}/estado`, { estado, motivo });
  }

  categories(): Observable<KnowledgeCategory[]> {
    return this.http.get<KnowledgeCategory[]>(`${this.kb}/categorias`);
  }

  createCategory(nombre: string, descripcion: string | null, orden: number): Observable<KnowledgeCategory> {
    return this.http.post<KnowledgeCategory>(`${this.kb}/categorias`, { nombre, descripcion, orden });
  }

  articles(q: { categoriaId?: number | null; texto?: string } = {}): Observable<PageResponse<KnowledgeArticle>> {
    let params = new HttpParams().set('tamano', 50);
    if (q.categoriaId) params = params.set('categoriaId', q.categoriaId);
    if (q.texto) params = params.set('texto', q.texto);
    return this.http.get<PageResponse<KnowledgeArticle>>(`${this.kb}/articulos`, { params });
  }

  saveArticle(id: number | null, payload: { categoriaId: number; titulo: string; slug: string | null; contenido: string; visibleClientes: boolean }): Observable<KnowledgeArticle> {
    return id ? this.http.put<KnowledgeArticle>(`${this.kb}/articulos/${id}`, payload) : this.http.post<KnowledgeArticle>(`${this.kb}/articulos`, payload);
  }

  articleAction(id: number, action: 'publicar' | 'archivar'): Observable<KnowledgeArticle> {
    return this.http.patch<KnowledgeArticle>(`${this.kb}/articulos/${id}/${action}`, {});
  }
}
