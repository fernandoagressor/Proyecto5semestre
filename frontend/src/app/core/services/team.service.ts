import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, of, shareReplay } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';

export interface TeamMember {
  id: number;
  nombre: string;
  nombreUsuario: string;
  estado: string;
  roles: string[];
}

/**
 * Equipo de la empresa (GET /empresas/actual/usuarios) para mostrar nombres en lugar de ids.
 * Se pide una vez por sesión de la pantalla; `name(id)` devuelve "Usuario #id" mientras carga.
 */
@Injectable({ providedIn: 'root' })
export class TeamService {
  private readonly http = inject(HttpClient);
  private readonly membersSignal = signal<TeamMember[]>([]);
  private request$ = this.fetch();

  readonly members = this.membersSignal.asReadonly();
  readonly byId = computed(() => new Map(this.membersSignal().map((m) => [m.id, m])));
  readonly advisors = computed(() =>
    this.membersSignal().filter((m) => m.estado === 'ACTIVO' && m.roles.some((r) => r.includes('ASESOR'))),
  );
  readonly active = computed(() => this.membersSignal().filter((m) => m.estado === 'ACTIVO'));

  load(): void {
    this.request$.subscribe((members) => this.membersSignal.set(members));
  }

  /** Vuelve a pedir el equipo (por ejemplo, al cambiar de sesión). */
  refresh(): void {
    this.request$ = this.fetch();
    this.load();
  }

  name(id: number | null | undefined): string {
    if (id === null || id === undefined) return '—';
    return this.byId().get(id)?.nombre ?? `Usuario #${id}`;
  }

  private fetch() {
    return this.http.get<TeamMember[]>(`${API_BASE_URL}/empresas/actual/usuarios`).pipe(
      catchError(() => of([] as TeamMember[])),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
  }
}
