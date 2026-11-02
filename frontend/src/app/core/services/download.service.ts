import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';

export type ExportFormat = 'PDF' | 'XLSX' | 'CSV';
export type ReportType = 'VENTAS' | 'RECAUDO' | 'CARTERA' | 'INVENTARIO' | 'COMISIONES';

/** Descargas generadas por la API (reportes, bitácora, documentos de contratos). */
@Injectable({ providedIn: 'root' })
export class DownloadService {
  private readonly http = inject(HttpClient);

  report(type: ReportType, format: ExportFormat, filters: { desde?: string; hasta?: string; proyectoId?: number | null }): Observable<void> {
    let params = new HttpParams().set('formato', format);
    if (filters.desde) params = params.set('desde', filters.desde);
    if (filters.hasta) params = params.set('hasta', filters.hasta);
    if (filters.proyectoId) params = params.set('proyectoId', filters.proyectoId);
    return this.save(`${API_BASE_URL}/exportaciones/reportes/${type.toLowerCase()}`, params, `${type.toLowerCase()}.${format.toLowerCase()}`);
  }

  audit(format: ExportFormat, filters: Record<string, string | number | null | undefined>): Observable<void> {
    let params = new HttpParams().set('formato', format);
    for (const [k, v] of Object.entries(filters)) if (v !== null && v !== undefined && v !== '') params = params.set(k, v);
    return this.save(`${API_BASE_URL}/exportaciones/auditoria`, params, `auditoria.${format.toLowerCase()}`);
  }

  contractDocument(contractId: number, kind: 'estado-cuenta' | 'paz-y-salvo' | 'cobro-mora'): Observable<void> {
    return this.save(`${API_BASE_URL}/contratos/${contractId}/documentos/${kind}`, new HttpParams(), `${kind}.pdf`);
  }

  portalStatement(contractId: number): Observable<void> {
    return this.save(`${API_BASE_URL}/portal/contratos/${contractId}/estado-cuenta`, new HttpParams(), 'estado-de-cuenta.pdf');
  }

  private save(url: string, params: HttpParams, fallback: string): Observable<void> {
    return this.http.get(url, { params, responseType: 'blob', observe: 'response' }).pipe(
      map((response: HttpResponse<Blob>) => saveBlob(response.body ?? new Blob(), fileName(response.headers.get('Content-Disposition')) ?? fallback)),
    );
  }
}

export function fileName(disposition: string | null): string | null {
  if (!disposition) return null;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (utf8) return decodeURIComponent(utf8[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1] : null;
}

export function saveBlob(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
