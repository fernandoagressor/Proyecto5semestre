import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  CustomerLotFlowPayload,
  CustomerLotFlowResult,
} from '../models/customer-lot-flow.models';

@Injectable({ providedIn: 'root' })
export class CustomerLotFlowService {
  private readonly http = inject(HttpClient);

  create(payload: CustomerLotFlowPayload): Observable<CustomerLotFlowResult> {
    return this.http.post<CustomerLotFlowResult>(`${API_BASE_URL}/ventas/cliente-lote`, payload);
  }
}
