import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import {
  Contract,
  ContractConditionsPayload,
  ContractCustomer,
  ContractCustomerRole,
  ContractSigner,
  ContractStatus,
  Customer,
  CustomerPayload,
  Reservation,
  ReservationStatus,
  SalesDocument,
  SignatureMethod,
  SignatureValidation,
  SignerType,
  CreateReservationPayload,
} from '../models/sales.models';

@Injectable({ providedIn: 'root' })
export class SalesService {
  private readonly http = inject(HttpClient);

  customers(options: { text?: string; status?: string; page?: number; size?: number } = {}): Observable<PageResponse<Customer>> {
    let params = this.pageParams(options.page, options.size);
    if (options.text) params = params.set('texto', options.text);
    if (options.status) params = params.set('estado', options.status);
    return this.http.get<PageResponse<Customer>>(`${API_BASE_URL}/clientes`, { params });
  }

  customer(id: number): Observable<Customer> {
    return this.http.get<Customer>(`${API_BASE_URL}/clientes/${id}`);
  }

  createCustomer(payload: CustomerPayload): Observable<Customer> {
    return this.http.post<Customer>(`${API_BASE_URL}/clientes`, payload);
  }

  updateCustomer(id: number, payload: CustomerPayload): Observable<Customer> {
    return this.http.put<Customer>(`${API_BASE_URL}/clientes/${id}`, payload);
  }

  changeCustomerStatus(id: number, estado: string): Observable<Customer> {
    return this.http.patch<Customer>(`${API_BASE_URL}/clientes/${id}/estado`, { estado });
  }

  reservations(options: { status?: ReservationStatus | ''; projectId?: number | null; page?: number; size?: number } = {}): Observable<PageResponse<Reservation>> {
    let params = this.pageParams(options.page, options.size);
    if (options.status) params = params.set('estado', options.status);
    if (options.projectId) params = params.set('proyectoId', options.projectId);
    return this.http.get<PageResponse<Reservation>>(`${API_BASE_URL}/reservas`, { params });
  }

  reservation(id: number): Observable<Reservation> {
    return this.http.get<Reservation>(`${API_BASE_URL}/reservas/${id}`);
  }

  createReservation(payload: CreateReservationPayload): Observable<Reservation> {
    return this.http.post<Reservation>(`${API_BASE_URL}/reservas`, payload);
  }

  extendReservation(id: number, fechaVencimiento: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`${API_BASE_URL}/reservas/${id}/vencimiento`, { fechaVencimiento });
  }

  confirmReservationPayment(id: number, comprobanteArchivoId: number | null): Observable<Reservation> {
    return this.http.patch<Reservation>(`${API_BASE_URL}/reservas/${id}/pago/confirmar`, { comprobanteArchivoId });
  }

  rejectReservationPayment(id: number, motivo: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`${API_BASE_URL}/reservas/${id}/pago/rechazar`, { motivo });
  }

  cancelReservation(id: number, motivo: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`${API_BASE_URL}/reservas/${id}/cancelar`, { motivo });
  }

  convertReservation(id: number, payload: ContractConditionsPayload): Observable<Contract> {
    return this.http.post<Contract>(`${API_BASE_URL}/reservas/${id}/contrato`, payload);
  }

  contracts(options: { status?: ContractStatus | ''; projectId?: number | null; page?: number; size?: number } = {}): Observable<PageResponse<Contract>> {
    let params = this.pageParams(options.page, options.size);
    if (options.status) params = params.set('estado', options.status);
    if (options.projectId) params = params.set('proyectoId', options.projectId);
    return this.http.get<PageResponse<Contract>>(`${API_BASE_URL}/contratos`, { params });
  }

  contract(id: number): Observable<Contract> {
    return this.http.get<Contract>(`${API_BASE_URL}/contratos/${id}`);
  }

  updateContract(id: number, payload: ContractConditionsPayload): Observable<Contract> {
    return this.http.put<Contract>(`${API_BASE_URL}/contratos/${id}`, payload);
  }

  addContractCustomer(id: number, clienteId: number, rol: ContractCustomerRole, porcentajeParticipacion: number): Observable<ContractCustomer> {
    return this.http.post<ContractCustomer>(`${API_BASE_URL}/contratos/${id}/clientes`, { clienteId, rol, porcentajeParticipacion });
  }

  addSigner(id: number, personaId: number, tipoFirmante: SignerType, ordenFirma: number, fechaProgramada: string | null): Observable<ContractSigner> {
    return this.http.post<ContractSigner>(`${API_BASE_URL}/contratos/${id}/firmantes`, { personaId, tipoFirmante, ordenFirma, fechaProgramada });
  }

  attachContractDocument(id: number, archivoId: number, motivo: string): Observable<unknown> {
    return this.http.post(`${API_BASE_URL}/contratos/${id}/versiones`, { archivoId, motivo });
  }

  startNegotiation(id: number): Observable<Contract> {
    return this.http.patch<Contract>(`${API_BASE_URL}/contratos/${id}/negociacion`, {});
  }

  sendForSignature(id: number): Observable<Contract> {
    return this.http.patch<Contract>(`${API_BASE_URL}/contratos/${id}/enviar-firma`, {});
  }

  registerSignature(id: number, signerId: number, payload: {
    metodo: SignatureMethod;
    proveedor: string | null;
    referenciaExterna: string | null;
    hashDocumento: string;
    ipOrigen: string | null;
    firmadaEn: string | null;
    estadoValidacion: SignatureValidation;
    archivoFirmaId: number;
  }): Observable<Contract> {
    return this.http.post<Contract>(`${API_BASE_URL}/contratos/${id}/firmantes/${signerId}/firmas`, payload);
  }

  cancelContract(id: number, motivo: string): Observable<Contract> {
    return this.http.patch<Contract>(`${API_BASE_URL}/contratos/${id}/anular`, { motivo });
  }

  uploadDocument(projectId: number | null, file: File, classification = 'CONFIDENCIAL'): Observable<SalesDocument> {
    let params = new HttpParams().set('clasificacion', classification);
    if (projectId) params = params.set('proyectoId', projectId);
    const body = new FormData();
    body.append('archivo', file, file.name);
    return this.http.post<SalesDocument>(`${API_BASE_URL}/ventas/documentos`, body, { params });
  }

  downloadDocument(id: number): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/ventas/documentos/${id}`, { responseType: 'blob' });
  }

  private pageParams(page = 0, size = 50): HttpParams {
    return new HttpParams().set('pagina', page).set('tamano', size);
  }
}
