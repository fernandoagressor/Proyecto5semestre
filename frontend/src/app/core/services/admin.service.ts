import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  AdminCompany,
  AdminProject,
  AdminRole,
  AdminUser,
  AdminUserCreateInput,
  AdminUserUpdateInput,
  CompanyCreateInput,
  CompanyStatus,
  CompanyUpdateInput,
  GlobalAdminAnalytics,
  GlobalAdminSummary,
  PermissionItem,
  ProjectInput,
  RoleInput,
} from '../models/admin.models';
import { PageResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/admin`;

  summary(): Observable<GlobalAdminSummary> {
    return this.http.get<GlobalAdminSummary>(`${this.base}/resumen`);
  }

  analytics(): Observable<GlobalAdminAnalytics> {
    return this.http.get<GlobalAdminAnalytics>(`${this.base}/analitica`);
  }

  companies(filters: { text?: string; status?: string; page?: number; size?: number } = {}): Observable<PageResponse<AdminCompany>> {
    let params = new HttpParams()
      .set('pagina', filters.page ?? 0)
      .set('tamano', filters.size ?? 50);
    if (filters.text) params = params.set('texto', filters.text);
    if (filters.status) params = params.set('estado', filters.status);
    return this.http.get<PageResponse<AdminCompany>>(`${this.base}/empresas`, { params });
  }

  createCompany(payload: CompanyCreateInput): Observable<AdminCompany> {
    return this.http.post<AdminCompany>(`${this.base}/empresas`, payload);
  }

  updateCompany(companyId: number, payload: CompanyUpdateInput): Observable<AdminCompany> {
    return this.http.put<AdminCompany>(`${this.base}/empresas/${companyId}`, payload);
  }

  changeCompanyStatus(company: AdminCompany, status: CompanyStatus, reason: string): Observable<AdminCompany> {
    return this.http.patch<AdminCompany>(`${this.base}/empresas/${company.id}/estado`, {
      estado: status,
      motivo: reason,
      version: company.version,
    });
  }

  users(companyId: number, text = '', status = ''): Observable<AdminUser[]> {
    let params = new HttpParams();
    if (text) params = params.set('texto', text);
    if (status) params = params.set('estado', status);
    return this.http.get<AdminUser[]>(`${this.base}/empresas/${companyId}/usuarios`, { params });
  }

  createUser(companyId: number, payload: AdminUserCreateInput): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/empresas/${companyId}/usuarios`, payload);
  }

  updateUser(companyId: number, userId: number, payload: AdminUserUpdateInput): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}/empresas/${companyId}/usuarios/${userId}`, payload);
  }

  changeUserStatus(companyId: number, user: AdminUser, status: AdminUser['estado'], reason: string): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.base}/empresas/${companyId}/usuarios/${user.id}/estado`, {
      estado: status,
      motivo: reason,
      version: user.version,
    });
  }

  resetCredentials(companyId: number, userId: number, temporaryPassword: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/empresas/${companyId}/usuarios/${userId}/credenciales`, {
      passwordTemporal: temporaryPassword,
      motivo: reason,
    });
  }

  roles(companyId: number): Observable<AdminRole[]> {
    return this.http.get<AdminRole[]>(`${this.base}/empresas/${companyId}/roles`);
  }

  permissions(): Observable<PermissionItem[]> {
    return this.http.get<PermissionItem[]>(`${this.base}/permisos`);
  }

  createRole(companyId: number, payload: RoleInput): Observable<AdminRole> {
    return this.http.post<AdminRole>(`${this.base}/empresas/${companyId}/roles`, payload);
  }

  updateRole(companyId: number, roleId: number, payload: RoleInput): Observable<AdminRole> {
    return this.http.put<AdminRole>(`${this.base}/empresas/${companyId}/roles/${roleId}`, payload);
  }

  projects(companyId: number): Observable<PageResponse<AdminProject>> {
    return this.http.get<PageResponse<AdminProject>>(`${this.base}/empresas/${companyId}/proyectos`, {
      params: new HttpParams().set('pagina', 0).set('tamano', 100),
    });
  }

  createProject(companyId: number, payload: ProjectInput): Observable<AdminProject> {
    return this.http.post<AdminProject>(`${this.base}/empresas/${companyId}/proyectos`, payload);
  }

  updateProject(companyId: number, projectId: number, payload: ProjectInput): Observable<AdminProject> {
    return this.http.put<AdminProject>(`${this.base}/empresas/${companyId}/proyectos/${projectId}`, payload);
  }
}
