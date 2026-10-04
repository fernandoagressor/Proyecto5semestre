import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import {
  Block,
  BlockPayload,
  CreateLotPayload,
  InventorySummary,
  Lot,
  LotPriceHistory,
  LotStateHistory,
  LotStatus,
  ProjectStage,
  StagePayload,
  UpdateLotPayload,
} from '../models/inventory.models';

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly http = inject(HttpClient);

  summary(projectId: number): Observable<InventorySummary> {
    return this.http.get<InventorySummary>(`${this.projectUrl(projectId)}/inventario/resumen`);
  }

  stages(projectId: number): Observable<ProjectStage[]> {
    return this.http.get<ProjectStage[]>(`${this.projectUrl(projectId)}/etapas`);
  }

  createStage(projectId: number, payload: StagePayload): Observable<ProjectStage> {
    return this.http.post<ProjectStage>(`${this.projectUrl(projectId)}/etapas`, payload);
  }

  updateStage(projectId: number, stageId: number, payload: StagePayload): Observable<ProjectStage> {
    return this.http.put<ProjectStage>(`${this.projectUrl(projectId)}/etapas/${stageId}`, payload);
  }

  blocks(projectId: number, stageId: number): Observable<Block[]> {
    return this.http.get<Block[]>(`${this.projectUrl(projectId)}/etapas/${stageId}/manzanas`);
  }

  createBlock(projectId: number, stageId: number, payload: BlockPayload): Observable<Block> {
    return this.http.post<Block>(`${this.projectUrl(projectId)}/etapas/${stageId}/manzanas`, payload);
  }

  listLots(options: {
    projectId: number;
    state?: string;
    stageId?: number | null;
    blockId?: number | null;
    text?: string;
    minArea?: number | null;
    maxArea?: number | null;
    minPrice?: number | null;
    maxPrice?: number | null;
    page?: number;
    size?: number;
  }): Observable<PageResponse<Lot>> {
    let params = new HttpParams()
      .set('pagina', options.page ?? 0)
      .set('tamano', options.size ?? 20);
    if (options.state) params = params.set('estado', options.state);
    if (options.stageId) params = params.set('etapaId', options.stageId);
    if (options.blockId) params = params.set('manzanaId', options.blockId);
    if (options.text) params = params.set('texto', options.text);
    if (options.minArea != null) params = params.set('areaMinima', options.minArea);
    if (options.maxArea != null) params = params.set('areaMaxima', options.maxArea);
    if (options.minPrice != null) params = params.set('valorMinimo', options.minPrice);
    if (options.maxPrice != null) params = params.set('valorMaximo', options.maxPrice);
    return this.http.get<PageResponse<Lot>>(`${this.projectUrl(options.projectId)}/lotes`, { params });
  }

  lot(projectId: number, lotId: number): Observable<Lot> {
    return this.http.get<Lot>(`${this.projectUrl(projectId)}/lotes/${lotId}`);
  }

  createLot(projectId: number, payload: CreateLotPayload): Observable<Lot> {
    return this.http.post<Lot>(`${this.projectUrl(projectId)}/lotes`, payload);
  }

  updateLot(projectId: number, lotId: number, payload: UpdateLotPayload): Observable<Lot> {
    return this.http.put<Lot>(`${this.projectUrl(projectId)}/lotes/${lotId}`, payload);
  }

  changeLotState(projectId: number, lotId: number, estado: LotStatus, motivo: string): Observable<Lot> {
    return this.http.patch<Lot>(`${this.projectUrl(projectId)}/lotes/${lotId}/estado`, { estado, motivo });
  }

  changeLotPrice(projectId: number, lotId: number, valor: number, motivo: string): Observable<Lot> {
    return this.http.patch<Lot>(`${this.projectUrl(projectId)}/lotes/${lotId}/precio`, { valor, motivo });
  }

  stateHistory(projectId: number, lotId: number): Observable<LotStateHistory[]> {
    return this.http.get<LotStateHistory[]>(`${this.projectUrl(projectId)}/lotes/${lotId}/historial-estados`);
  }

  priceHistory(projectId: number, lotId: number): Observable<LotPriceHistory[]> {
    return this.http.get<LotPriceHistory[]>(`${this.projectUrl(projectId)}/lotes/${lotId}/historial-precios`);
  }

  private projectUrl(projectId: number): string {
    return `${API_BASE_URL}/proyectos/${projectId}`;
  }
}
