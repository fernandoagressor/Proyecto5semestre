import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import {
  CollectionChannel,
  CollectionManagement,
  FollowUpInstallment,
  OverdueInstallment,
  PaymentApplication,
  PaymentMethod,
  PaymentPlan,
  PaymentStatus,
  PortfolioConfiguration,
  PortfolioContract,
  PortfolioPayment,
  PortfolioSummary,
} from '../models/portfolio.models';

@Injectable({ providedIn: 'root' })
export class PortfolioService {
  private readonly http = inject(HttpClient);

  summary(): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(`${API_BASE_URL}/cartera/resumen`);
  }

  contracts(): Observable<PortfolioContract[]> {
    return this.http.get<PortfolioContract[]>(`${API_BASE_URL}/cartera/contratos`);
  }

  plans(contractId: number): Observable<PaymentPlan[]> {
    return this.http.get<PaymentPlan[]>(`${API_BASE_URL}/cartera/planes-pago`, {
      params: new HttpParams().set('contratoId', contractId),
    });
  }

  synchronizePlan(contractId: number): Observable<PaymentPlan> {
    return this.http.post<PaymentPlan>(
      `${API_BASE_URL}/cartera/planes-pago/contratos/${contractId}/sincronizar`,
      {},
    );
  }

  paymentMethods(): Observable<PaymentMethod[]> {
    return this.http.get<PaymentMethod[]>(`${API_BASE_URL}/cartera/metodos-pago`);
  }

  payments(options: { status?: PaymentStatus | ''; contractId?: number | null } = {}): Observable<PageResponse<PortfolioPayment>> {
    let params = new HttpParams().set('pagina', 0).set('tamano', 100);
    if (options.status) params = params.set('estado', options.status);
    if (options.contractId) params = params.set('contratoId', options.contractId);
    return this.http.get<PageResponse<PortfolioPayment>>(`${API_BASE_URL}/pagos`, { params });
  }

  registerPayment(payload: {
    contratoId: number;
    reservaId: null;
    clienteId: number;
    metodoPagoId: number;
    fechaPago: string;
    valor: number;
    referencia: string | null;
    comprobanteId: number | null;
    origen: 'MANUAL';
    observaciones: string | null;
  }): Observable<PortfolioPayment> {
    return this.http.post<PortfolioPayment>(`${API_BASE_URL}/pagos`, payload);
  }

  submitPayment(id: number): Observable<PortfolioPayment> {
    return this.http.patch<PortfolioPayment>(`${API_BASE_URL}/pagos/${id}/revision`, {});
  }

  confirmPayment(id: number, applications: PaymentApplication[], reason: string): Observable<PortfolioPayment> {
    return this.http.patch<PortfolioPayment>(`${API_BASE_URL}/pagos/${id}/confirmar`, {
      aplicaciones: applications.map(({ cuotaId, facturaId, concepto, valor }) => ({
        cuotaId,
        facturaId,
        concepto,
        valor,
      })),
      motivo: reason,
    });
  }

  rejectPayment(id: number, reason: string): Observable<PortfolioPayment> {
    return this.http.patch<PortfolioPayment>(`${API_BASE_URL}/pagos/${id}/rechazar`, {
      motivo: reason,
    });
  }

  requestReversal(id: number, reason: string): Observable<unknown> {
    return this.http.post(`${API_BASE_URL}/pagos/${id}/reversiones`, {
      motivo: reason,
      evidenciaId: null,
    });
  }

  overdue(): Observable<PageResponse<OverdueInstallment>> {
    return this.http.get<PageResponse<OverdueInstallment>>(`${API_BASE_URL}/cartera/mora`, {
      params: new HttpParams().set('pagina', 0).set('tamano', 100),
    });
  }

  updateOverdue(): Observable<number> {
    return this.http.post<number>(`${API_BASE_URL}/cartera/mora/actualizar`, {});
  }

  followUpInstallments(): Observable<PageResponse<FollowUpInstallment>> {
    return this.http.get<PageResponse<FollowUpInstallment>>(`${API_BASE_URL}/cartera/seguimiento`, {
      params: new HttpParams().set('pagina', 0).set('tamano', 100),
    });
  }

  collections(contractId: number): Observable<CollectionManagement[]> {
    return this.http.get<CollectionManagement[]>(`${API_BASE_URL}/cartera/gestiones-cobro`, {
      params: new HttpParams().set('contratoId', contractId),
    });
  }

  createCollection(payload: {
    contratoId: number;
    cuotaId: number | null;
    canal: CollectionChannel;
    resultado: string;
    detalle: string | null;
    promesaPagoFecha: string | null;
    promesaPagoValor: number | null;
  }): Observable<CollectionManagement> {
    return this.http.post<CollectionManagement>(`${API_BASE_URL}/cartera/gestiones-cobro`, payload);
  }

  configuration(): Observable<PortfolioConfiguration> {
    return this.http.get<PortfolioConfiguration>(`${API_BASE_URL}/cartera/configuracion`);
  }

  updateConfiguration(payload: Omit<PortfolioConfiguration, 'id' | 'empresaId' | 'version'>): Observable<PortfolioConfiguration> {
    return this.http.put<PortfolioConfiguration>(`${API_BASE_URL}/cartera/configuracion`, payload);
  }
}
