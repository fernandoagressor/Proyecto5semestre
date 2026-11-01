import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  AdminRole,
  AdminUser,
  AdminUserCreateInput,
  AdminUserUpdateInput,
  PermissionItem,
  RoleInput,
} from '../models/admin.models';

/** Usuarios, roles y permisos de la empresa de la sesión (`/api/v1/seguridad`). */
@Injectable({ providedIn: 'root' })
export class CompanySecurityService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/seguridad`;

  users(text = '', status = ''): Observable<AdminUser[]> {
    let params = new HttpParams();
    if (text) params = params.set('texto', text);
    if (status) params = params.set('estado', status);
    return this.http.get<AdminUser[]>(`${this.base}/usuarios`, { params });
  }

  createUser(payload: AdminUserCreateInput): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/usuarios`, payload);
  }

  updateUser(id: number, payload: AdminUserUpdateInput): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}/usuarios/${id}`, payload);
  }

  changeUserStatus(user: AdminUser, estado: AdminUser['estado'], motivo: string): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.base}/usuarios/${user.id}/estado`, { estado, motivo, version: user.version });
  }

  resetCredentials(id: number, passwordTemporal: string, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/usuarios/${id}/credenciales`, { passwordTemporal, motivo });
  }

  roles(): Observable<AdminRole[]> {
    return this.http.get<AdminRole[]>(`${this.base}/roles`);
  }

  permissions(): Observable<PermissionItem[]> {
    return this.http.get<PermissionItem[]>(`${this.base}/permisos`);
  }

  createRole(payload: RoleInput): Observable<AdminRole> {
    return this.http.post<AdminRole>(`${this.base}/roles`, payload);
  }

  updateRole(id: number, payload: RoleInput): Observable<AdminRole> {
    return this.http.put<AdminRole>(`${this.base}/roles/${id}`, payload);
  }
}
