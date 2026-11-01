import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PageResponse } from '../models/api.models';
import {
  Budget,
  BudgetRequest,
  BudgetStatus,
  Commission,
  CommissionRule,
  CommissionRuleRequest,
  CommissionStatus,
  Cost,
  CostCategory,
  CostCategoryType,
  CostRequest,
  CostStatus,
  Goal,
  GoalProgress,
  GoalRequest,
  GoalStatus,
  Profitability,
} from '../models/gerencia.models';

type Query = Record<string, string | number | boolean | null | undefined>;

function params(query: Query): HttpParams {
  let p = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') p = p.set(key, value);
  }
  return p;
}

/** Metas comerciales, comisiones, presupuestos, costos y rentabilidad (módulo Gerencia). */
@Injectable({ providedIn: 'root' })
export class GerenciaService {
  private readonly http = inject(HttpClient);
  private readonly metas = `${API_BASE_URL}/metas-comerciales`;
  private readonly comisiones = `${API_BASE_URL}/comisiones`;
  private readonly finanzas = `${API_BASE_URL}/gerencia`;

  // ---------- Metas ----------
  goals(q: { periodo?: string; proyectoId?: number | null; asesorId?: number | null; estado?: GoalStatus | ''; pagina?: number; tamano?: number }): Observable<PageResponse<Goal>> {
    return this.http.get<PageResponse<Goal>>(this.metas, { params: params({ tamano: 100, ...q }) });
  }
  createGoal(request: GoalRequest): Observable<Goal> {
    return this.http.post<Goal>(this.metas, request);
  }
  updateGoal(id: number, valorMeta: number, cantidadMeta: number): Observable<Goal> {
    return this.http.put<Goal>(`${this.metas}/${id}`, { valorMeta, cantidadMeta });
  }
  approveGoal(id: number): Observable<Goal> {
    return this.http.patch<Goal>(`${this.metas}/${id}/aprobar`, {});
  }
  closeGoal(id: number): Observable<Goal> {
    return this.http.patch<Goal>(`${this.metas}/${id}/cerrar`, {});
  }
  goalProgress(id: number): Observable<GoalProgress> {
    return this.http.get<GoalProgress>(`${this.metas}/${id}/cumplimiento`);
  }

  // ---------- Comisiones ----------
  rules(q: { proyectoId?: number | null; activo?: boolean | null } = {}): Observable<PageResponse<CommissionRule>> {
    return this.http.get<PageResponse<CommissionRule>>(`${this.comisiones}/reglas`, { params: params({ tamano: 100, ...q }) });
  }
  createRule(request: CommissionRuleRequest): Observable<CommissionRule> {
    return this.http.post<CommissionRule>(`${this.comisiones}/reglas`, request);
  }
  closeRule(id: number, fecha: string): Observable<CommissionRule> {
    return this.http.patch<CommissionRule>(`${this.comisiones}/reglas/${id}/cerrar`, { fecha });
  }
  commissions(q: { proyectoId?: number | null; asesorId?: number | null; estado?: CommissionStatus | ''; pagina?: number; tamano?: number }): Observable<PageResponse<Commission>> {
    return this.http.get<PageResponse<Commission>>(this.comisiones, { params: params({ tamano: 100, ...q }) });
  }
  projectCommission(contratoId: number, reglaComisionId: number, baseCalculo: number | null): Observable<Commission> {
    return this.http.post<Commission>(this.comisiones, { contratoId, reglaComisionId, baseCalculo });
  }
  accrueCommission(id: number, fecha: string): Observable<Commission> {
    return this.http.patch<Commission>(`${this.comisiones}/${id}/causar`, { fecha });
  }
  approveCommission(id: number): Observable<Commission> {
    return this.http.patch<Commission>(`${this.comisiones}/${id}/aprobar`, {});
  }
  payCommission(id: number, fecha: string): Observable<Commission> {
    return this.http.patch<Commission>(`${this.comisiones}/${id}/pagar`, { fecha });
  }
  voidCommission(id: number): Observable<Commission> {
    return this.http.patch<Commission>(`${this.comisiones}/${id}/anular`, {});
  }

  // ---------- Presupuestos y costos ----------
  categories(): Observable<CostCategory[]> {
    return this.http.get<CostCategory[]>(`${this.finanzas}/categorias-costo`);
  }
  createCategory(codigo: string, nombre: string, tipo: CostCategoryType): Observable<CostCategory> {
    return this.http.post<CostCategory>(`${this.finanzas}/categorias-costo`, { codigo, nombre, tipo });
  }
  deactivateCategory(id: number): Observable<CostCategory> {
    return this.http.patch<CostCategory>(`${this.finanzas}/categorias-costo/${id}/inactivar`, {});
  }
  budgets(q: { proyectoId?: number | null; estado?: BudgetStatus | '' }): Observable<PageResponse<Budget>> {
    return this.http.get<PageResponse<Budget>>(`${this.finanzas}/presupuestos`, { params: params({ tamano: 100, ...q }) });
  }
  createBudget(request: BudgetRequest): Observable<Budget> {
    return this.http.post<Budget>(`${this.finanzas}/presupuestos`, request);
  }
  budgetAction(id: number, action: 'aprobar' | 'cerrar' | 'anular'): Observable<Budget> {
    return this.http.patch<Budget>(`${this.finanzas}/presupuestos/${id}/${action}`, {});
  }
  costs(q: { proyectoId?: number | null; estado?: CostStatus | ''; desde?: string; hasta?: string; pagina?: number }): Observable<PageResponse<Cost>> {
    return this.http.get<PageResponse<Cost>>(`${this.finanzas}/costos`, { params: params({ tamano: 100, ...q }) });
  }
  createCost(request: CostRequest): Observable<Cost> {
    return this.http.post<Cost>(`${this.finanzas}/costos`, request);
  }
  costAction(id: number, action: 'aprobar' | 'pagar' | 'anular'): Observable<Cost> {
    return this.http.patch<Cost>(`${this.finanzas}/costos/${id}/${action}`, {});
  }
  profitability(q: { proyectoId?: number | null; desde: string; hasta: string }): Observable<Profitability> {
    return this.http.get<Profitability>(`${this.finanzas}/rentabilidad`, { params: params(q) });
  }
}
