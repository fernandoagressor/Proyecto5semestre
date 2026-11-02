import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';

export interface WithdrawalSettlement {
  contratoId: number;
  contrato: string;
  estadoContrato: string;
  cliente: string;
  proyecto: string;
  lote: string;
  valorContrato: number;
  totalPagado: number;
  porcentajePenalidad: number;
  valorPenalidad: number;
  otrosDescuentos: number;
  valorDevolver: number;
  cuotasAbiertas: number;
  comisionesPendientes: number;
  comisionesPagadas: number;
}

export interface Withdrawal {
  id: number;
  codigo: string;
  contratoId: number;
  contrato: string;
  cliente: string;
  proyecto: string;
  lote: string;
  origen: 'CLIENTE' | 'EMPRESA';
  motivo: string;
  totalPagado: number;
  porcentajePenalidad: number;
  valorPenalidad: number;
  otrosDescuentos: number;
  valorDevolver: number;
  fechaDevolucion: string | null;
  observaciones: string | null;
  estado: 'SOLICITADO' | 'APROBADO' | 'RECHAZADO';
  solicitadoPor: number;
  revisadoPor: number | null;
  revisadoEn: string | null;
  comentarioRevision: string | null;
  cuotasCanceladas: number;
  comisionesAnuladas: number;
  version: number;
  creadoEn: string;
}

export interface WithdrawalRequest {
  contratoId: number;
  origen: 'CLIENTE' | 'EMPRESA';
  motivo: string;
  porcentajePenalidad: number;
  otrosDescuentos: number;
  fechaDevolucion: string | null;
  observaciones: string | null;
}

/** Desistimientos de compra (`/api/v1/desistimientos`). */
@Injectable({ providedIn: 'root' })
export class WithdrawalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/desistimientos`;

  list(estado = ''): Observable<Withdrawal[]> {
    const params = estado ? new HttpParams().set('estado', estado) : undefined;
    return this.http.get<Withdrawal[]>(this.base, { params });
  }

  settlement(contractId: number, penalty: number, other: number): Observable<WithdrawalSettlement> {
    const params = new HttpParams().set('contratoId', contractId).set('porcentajePenalidad', penalty).set('otrosDescuentos', other);
    return this.http.get<WithdrawalSettlement>(`${this.base}/liquidacion`, { params });
  }

  request(payload: WithdrawalRequest): Observable<Withdrawal> {
    return this.http.post<Withdrawal>(this.base, payload);
  }

  approve(item: Withdrawal, comentario: string | null): Observable<Withdrawal> {
    return this.http.patch<Withdrawal>(`${this.base}/${item.id}/aprobar`, { comentario, version: item.version });
  }

  reject(item: Withdrawal, comentario: string): Observable<Withdrawal> {
    return this.http.patch<Withdrawal>(`${this.base}/${item.id}/rechazar`, { comentario, version: item.version });
  }

  actaUrl(id: number): string {
    return `${this.base}/${id}/acta`;
  }
}
