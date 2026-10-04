import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  ClientDashboard,
  CommercialDashboard,
  DashboardDateRange,
  DashboardData,
  DashboardKind,
  ManagementDashboard,
  PortfolioDashboard,
  ServiceDashboard,
} from '../models/dashboard.models';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  availableKind(): DashboardKind {
    if (this.auth.hasAuthority('DASHBOARD_GERENCIAL_VER')) return 'gerencial';
    if (this.auth.hasAuthority('DASHBOARD_COMERCIAL_VER')) return 'comercial';
    if (this.auth.hasAuthority('DASHBOARD_CARTERA_VER')) return 'cartera';
    if (this.auth.hasAuthority('DASHBOARD_SERVICIO_VER')) return 'servicio';
    return 'cliente';
  }

  load(
    kind: DashboardKind,
    projectId?: number | null,
    range?: DashboardDateRange,
  ): Observable<DashboardData> {
    let params = new HttpParams();
    if (projectId !== null && projectId !== undefined) {
      params = params.set('proyectoId', projectId);
    }

    if (kind === 'cartera' && range?.hasta) {
      params = params.set('corte', range.hasta);
    } else if (range && kind !== 'cliente') {
      params = params.set('desde', range.desde).set('hasta', range.hasta);
    }

    switch (kind) {
      case 'gerencial':
        return this.http.get<ManagementDashboard>(`${API_BASE_URL}/dashboards/gerencial`, {
          params,
        });
      case 'comercial':
        return this.http.get<CommercialDashboard>(`${API_BASE_URL}/dashboards/comercial`, {
          params,
        });
      case 'cartera':
        return this.http.get<PortfolioDashboard>(`${API_BASE_URL}/dashboards/cartera`, {
          params,
        });
      case 'servicio':
        return this.http.get<ServiceDashboard>(`${API_BASE_URL}/dashboards/servicio`, {
          params,
        });
      default:
        return this.http.get<ClientDashboard>(`${API_BASE_URL}/portal/resumen`);
    }
  }
}
