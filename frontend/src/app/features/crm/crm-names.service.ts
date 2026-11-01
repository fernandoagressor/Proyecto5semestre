import { inject, Injectable, signal } from '@angular/core';
import { catchError, from, mergeMap, of } from 'rxjs';

import { Opportunity, Prospect } from '../../core/models/crm.models';
import { CrmService } from '../../core/services/crm.service';

const MAX_FETCH = 60;

/**
 * Nombres para las tarjetas y la agenda: la API devuelve ids (prospectoId, oportunidadId).
 * Se recuerdan los que ya llegaron en listas y se piden los que falten, de a 4 a la vez.
 * Se provee en la página del CRM para que lo compartan sus pestañas.
 */
@Injectable()
export class CrmNames {
  private readonly crm = inject(CrmService);
  private readonly prospects = signal<ReadonlyMap<number, string>>(new Map());
  private readonly opportunities = signal<ReadonlyMap<number, Opportunity>>(new Map());
  private readonly pending = new Set<string>();

  rememberProspects(list: Prospect[]): void {
    if (!list.length) return;
    this.prospects.update((m) => new Map([...m, ...list.map((p) => [p.id, p.nombres] as const)]));
  }

  rememberOpportunities(list: Opportunity[]): void {
    if (!list.length) return;
    this.opportunities.update((m) => new Map([...m, ...list.map((o) => [o.id, o] as const)]));
    this.ensureProspects(list.map((o) => o.prospectoId));
  }

  ensureProspects(ids: number[]): void {
    const missing = [...new Set(ids)].filter((id) => id && !this.prospects().has(id) && !this.pending.has(`p${id}`)).slice(0, MAX_FETCH);
    missing.forEach((id) => this.pending.add(`p${id}`));
    from(missing)
      .pipe(mergeMap((id) => this.crm.prospect(id).pipe(catchError(() => of(null))), 4))
      .subscribe((p) => p && this.rememberProspects([p]));
  }

  ensureOpportunities(ids: number[]): void {
    const missing = [...new Set(ids)].filter((id) => id && !this.opportunities().has(id) && !this.pending.has(`o${id}`)).slice(0, MAX_FETCH);
    missing.forEach((id) => this.pending.add(`o${id}`));
    from(missing)
      .pipe(mergeMap((id) => this.crm.opportunity(id).pipe(catchError(() => of(null))), 4))
      .subscribe((o) => o && this.rememberOpportunities([o]));
  }

  prospectName(id: number | null | undefined): string {
    if (!id) return '—';
    return this.prospects().get(id) ?? 'Cargando…';
  }

  opportunity(id: number): Opportunity | undefined {
    return this.opportunities().get(id);
  }

  /** Nombre del cliente de una oportunidad, o su código mientras llega. */
  opportunityTitle(id: number): string {
    const o = this.opportunities().get(id);
    if (!o) return `Oportunidad #${id}`;
    return this.prospects().get(o.prospectoId) ?? o.codigo;
  }
}
