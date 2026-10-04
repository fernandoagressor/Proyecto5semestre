import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { finalize, Observable, shareReplay, tap } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  AuthenticatedUser,
  ChangePasswordRequest,
  LoginRequest,
  TokenResponse,
} from '../models/auth.models';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(TokenStorageService);
  private readonly sessionState = signal(this.storage.read());
  private refreshRequest?: Observable<TokenResponse>;

  readonly session = this.sessionState.asReadonly();
  readonly user = computed<AuthenticatedUser | null>(() => this.sessionState()?.user ?? null);
  readonly isAuthenticated = computed(() => Boolean(this.sessionState()?.refreshToken));
  readonly initials = computed(() => {
    const user = this.user();
    if (!user) return 'NC';
    return user.nombreUsuario
      .split(/[._\-\s]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  });

  login(payload: LoginRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${API_BASE_URL}/auth/login`, payload).pipe(
      tap((response) => this.activate(response)),
    );
  }

  refreshSession(): Observable<TokenResponse> {
    if (this.refreshRequest) return this.refreshRequest;

    const refreshToken = this.sessionState()?.refreshToken;
    if (!refreshToken) {
      throw new Error('No hay una sesión renovable');
    }

    this.refreshRequest = this.http
      .post<TokenResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken })
      .pipe(
        tap((response) => this.activate(response)),
        finalize(() => (this.refreshRequest = undefined)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.refreshRequest;
  }

  logoutRequest(): Observable<void> | null {
    const refreshToken = this.sessionState()?.refreshToken;
    if (!refreshToken) return null;
    return this.http.post<void>(`${API_BASE_URL}/auth/logout`, { refreshToken });
  }

  changePassword(payload: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/auth/cambiar-password`, payload);
  }

  switchCompany(companyId: number): Observable<TokenResponse> {
    const refreshToken = this.sessionState()?.refreshToken;
    if (!refreshToken) {
      throw new Error('No hay una sesión renovable');
    }
    return this.http
      .post<TokenResponse>(`${API_BASE_URL}/auth/contexto`, { empresaId: companyId, refreshToken })
      .pipe(tap((response) => this.activate(response)));
  }

  clearSession(): void {
    this.storage.clear();
    this.sessionState.set(null);
  }

  accessToken(): string | null {
    return this.sessionState()?.accessToken ?? null;
  }

  refreshToken(): string | null {
    return this.sessionState()?.refreshToken ?? null;
  }

  hasAuthority(authority: string): boolean {
    const user = this.user();
    if (!user) return false;
    if (authority.startsWith('ROLE_')) {
      return user.roles.includes(authority.slice(5));
    }
    return user.permisos.includes(authority);
  }

  hasAny(authorities: readonly string[]): boolean {
    return authorities.length === 0 || authorities.some((authority) => this.hasAuthority(authority));
  }

  hasAll(authorities: readonly string[]): boolean {
    return authorities.every((authority) => this.hasAuthority(authority));
  }

  private activate(response: TokenResponse): void {
    this.sessionState.set(this.storage.write(response));
  }
}
