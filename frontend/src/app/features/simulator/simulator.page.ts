import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, of, Subject, switchMap } from 'rxjs';

import { Opportunity, OpportunityLot, Periodicity, Prospect, Simulation } from '../../core/models/crm.models';
import { Lot } from '../../core/models/inventory.models';
import { AuthService } from '../../core/services/auth.service';
import { CrmService } from '../../core/services/crm.service';
import { InventoryService } from '../../core/services/inventory.service';
import { MoneyInputDirective } from '../../shared/directives/money-input.directive';
import { apiErrorMessage, formatCop, formatCopCompact, localDateTimeIso, NX_UI, todayIso, ToastService } from '../../shared/ui';
import { FormsModule } from '@angular/forms';
import { byYear, frenchPlan, PERIODS_PER_YEAR } from './amortization';

const PERIODICITIES: { value: Periodicity; label: string }[] = [
  { value: 'MENSUAL', label: 'Mensual' },
  { value: 'BIMESTRAL', label: 'Bimestral' },
  { value: 'TRIMESTRAL', label: 'Trimestral' },
  { value: 'SEMESTRAL', label: 'Semestral' },
  { value: 'ANUAL', label: 'Anual' },
];

function firstOfNextMonth(day: number): string {
  const d = new Date();
  return todayIso(new Date(d.getFullYear(), d.getMonth() + 1, day));
}

/**
 * Simulador de plan de pagos (vista "Simulacion-Plan" del diseño). Vista previa inmediata con
 * cuota fija; al guardar, la API calcula el plan oficial y queda la simulación para enviarla.
 */
@Component({
  selector: 'app-simulator-page',
  imports: [...NX_UI, FormsModule, MoneyInputDirective, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './simulator.page.html',
  styleUrl: './simulator.page.scss',
})
export class SimulatorPage {
  private readonly crm = inject(CrmService);
  private readonly inventory = inject(InventoryService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly canCreate = this.auth.hasAuthority('SIMULACIONES_CREAR');
  readonly canSend = this.auth.hasAuthority('SIMULACIONES_ENVIAR');
  readonly periodicities = PERIODICITIES;

  // Selección
  readonly opportunityId = signal<number | null>(null);
  readonly opportunity = signal<Opportunity | null>(null);
  readonly prospect = signal<Prospect | null>(null);
  readonly lots = signal<Lot[]>([]);
  readonly relations = signal<OpportunityLot[]>([]);
  readonly lotId = signal<number | null>(null);
  readonly searchText = signal('');
  readonly results = signal<{ o: Opportunity; name: string }[]>([]);
  private readonly search$ = new Subject<string>();

  // Condiciones
  readonly discount = signal<number | null>(0);
  readonly downPct = signal(30);
  readonly installments = signal(36);
  readonly ratePct = signal(12);
  readonly periodicity = signal<Periodicity>('MENSUAL');
  readonly payDay = signal(5);
  readonly firstDate = signal(firstOfNextMonth(5));
  readonly validDays = signal(15);

  readonly saving = signal(false);
  /** Última simulación guardada y las condiciones con las que se guardó. */
  private readonly savedState = signal<{ sim: Simulation; key: string } | null>(null);
  private readonly conditionsKey = computed(() =>
    JSON.stringify([this.lotId(), this.discount(), this.downPct(), this.installments(), this.ratePct(), this.periodicity(), this.firstDate()]),
  );
  /** La simulación guardada solo cuenta mientras no se cambien las condiciones. */
  readonly saved = computed(() => {
    const st = this.savedState();
    return st && st.key === this.conditionsKey() ? st.sim : null;
  });
  readonly history = signal<Simulation[]>([]);
  readonly showAll = signal(false);
  readonly loading = signal(false);

  readonly lot = computed(() => this.lots().find((l) => l.id === this.lotId()) ?? null);
  readonly lotOptions = computed(() => {
    const linked = new Set(this.relations().map((r) => r.loteId));
    return [...this.lots()].sort((a, b) => Number(linked.has(b.id)) - Number(linked.has(a.id))).map((l) => ({ lot: l, linked: linked.has(l.id) }));
  });
  readonly price = computed(() => Number(this.lot()?.valorLista ?? 0));
  readonly net = computed(() => Math.max(0, this.price() - Number(this.discount() ?? 0)));
  readonly downPayment = computed(() => Math.round((this.net() * this.downPct()) / 100));
  readonly financed = computed(() => Math.max(0, this.net() - this.downPayment()));
  readonly plan = computed(() => frenchPlan(this.financed(), this.installments(), this.ratePct() / 100, this.periodicity(), this.firstDate()));
  /** Si ya se guardó, se muestra el plan oficial de la API. */
  readonly rows = computed(() => {
    const s = this.saved();
    if (s) return s.cuotas.map((c) => ({ numero: c.numero, fecha: c.fechaVencimiento, capital: Number(c.capital), interes: Number(c.interes), cuota: Number(c.valorTotal), saldo: Number(c.saldoProyectado) }));
    return this.plan().rows;
  });
  readonly visibleRows = computed(() => (this.showAll() ? this.rows() : this.rows().slice(0, 12)));
  readonly years = computed(() => {
    const list = byYear(this.rows());
    const max = Math.max(...list.map((y) => y.total), 1);
    return list.map((y) => ({ ...y, capH: (y.capital / max) * 100, intH: (y.interes / max) * 100, label: formatCopCompact(y.total) }));
  });
  readonly summary = computed(() => {
    const rows = this.rows();
    const interest = rows.reduce((s, r) => s + r.interes, 0);
    const first = rows[0]?.cuota ?? 0;
    return {
      installment: formatCop(first),
      title: `Cuota ${PERIODICITIES.find((p) => p.value === this.periodicity())?.label.toLowerCase()}`,
      down: formatCop(this.saved()?.cuotaInicial ?? this.downPayment()),
      interest: formatCop(interest),
      total: formatCop(Number(this.saved()?.cuotaInicial ?? this.downPayment()) + rows.reduce((s, r) => s + r.cuota, 0)),
      years: (this.installments() / PERIODS_PER_YEAR[this.periodicity()]).toLocaleString('es-CO', { maximumFractionDigits: 1 }),
    };
  });
  readonly subtitle = computed(() => [this.prospect()?.nombres, this.opportunity()?.codigo, this.lot()?.codigo].filter(Boolean).join(' · '));

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((p) => {
      const id = Number(p.get('oportunidad'));
      this.opportunityId.set(id || null);
    });
    effect(() => {
      const id = this.opportunityId();
      untracked(() => (id ? this.loadOpportunity(id) : this.reset()));
    });
    this.search$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((text) => this.crm.opportunities({ text, status: 'ABIERTA', size: 10 }).pipe(catchError(() => of(null)))),
        switchMap((page) => {
          const list = page?.contenido ?? [];
          return list.length ? forkJoin(list.map((o) => this.crm.prospect(o.prospectoId).pipe(catchError(() => of(null))))).pipe(
            switchMap((prospects) => of(list.map((o, i) => ({ o, name: prospects[i]?.nombres ?? o.codigo })))),
          ) : of([]);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((r) => this.results.set(r));
    this.search$.next('');
  }

  searchOpportunities(text: string): void {
    this.searchText.set(text);
    this.search$.next(text.trim());
  }

  pick(id: number): void {
    void this.router.navigate([], { queryParams: { oportunidad: id }, replaceUrl: true });
  }

  changeOpportunity(): void {
    void this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  setPayDay(day: number): void {
    this.payDay.set(day);
    this.firstDate.set(firstOfNextMonth(day));
  }

  save(): void {
    const o = this.opportunity();
    const lot = this.lot();
    if (!o || !lot) return;
    const valid = new Date();
    valid.setDate(valid.getDate() + this.validDays());
    valid.setHours(23, 59, 0, 0);
    this.saving.set(true);
    this.crm
      .createSimulation({
        oportunidadId: o.id,
        loteId: lot.id,
        descuento: Number(this.discount() ?? 0),
        cuotaInicial: this.downPayment(),
        numeroCuotas: this.installments(),
        tasaEfectivaAnual: Number((this.ratePct() / 100).toFixed(6)),
        periodicidad: this.periodicity(),
        fechaPrimeraCuota: this.firstDate(),
        vigenteHasta: localDateTimeIso(valid),
      })
      .subscribe({
        next: (s) => {
          this.saving.set(false);
          this.savedState.set({ sim: s, key: this.conditionsKey() });
          this.toast.success(`Simulación ${s.codigo} guardada. Vigente hasta el ${s.vigenteHasta.slice(0, 10)}.`);
          this.loadHistory(o.id);
        },
        error: (e) => {
          this.saving.set(false);
          this.toast.error(apiErrorMessage(e, 'No se pudo guardar la simulación.'));
        },
      });
  }

  send(s: Simulation): void {
    this.act(this.crm.sendSimulation(s.id), 'Simulación enviada al cliente.');
  }

  cancel(s: Simulation): void {
    this.act(this.crm.cancelSimulation(s.id), 'Simulación cancelada.');
  }

  open(s: Simulation): void {
    this.lotId.set(s.loteId);
    this.discount.set(Number(s.descuento));
    this.installments.set(s.numeroCuotas);
    this.ratePct.set(Number(s.tasaEfectivaAnual) * 100);
    this.periodicity.set(s.periodicidad);
    const net = Number(s.valorLote) - Number(s.descuento);
    this.downPct.set(net > 0 ? Math.round((Number(s.cuotaInicial) / net) * 100) : 0);
    if (s.cuotas[0]) this.firstDate.set(s.cuotas[0].fechaVencimiento);
    this.savedState.set({ sim: s, key: this.conditionsKey() });
  }

  money(v: number): string {
    return formatCop(v);
  }

  private act(request: ReturnType<CrmService['sendSimulation']>, message: string): void {
    this.saving.set(true);
    request.subscribe({
      next: (s) => {
        this.saving.set(false);
        this.toast.success(message);
        const st = this.savedState();
        if (st?.sim.id === s.id) this.savedState.set({ ...st, sim: { ...s, cuotas: s.cuotas?.length ? s.cuotas : st.sim.cuotas } });
        this.loadHistory(s.oportunidadId);
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.error(apiErrorMessage(e));
      },
    });
  }

  private loadOpportunity(id: number): void {
    this.loading.set(true);
    this.crm.opportunity(id).subscribe({
      next: (o) => {
        this.opportunity.set(o);
        forkJoin({
          prospect: this.crm.prospect(o.prospectoId).pipe(catchError(() => of(null))),
          relations: this.crm.opportunityLots(o.id).pipe(catchError(() => of([] as OpportunityLot[]))),
          lots: this.inventory.listLots({ projectId: o.proyectoId, size: 100 }).pipe(catchError(() => of(null))),
        }).subscribe(({ prospect, relations, lots }) => {
          this.prospect.set(prospect);
          this.relations.set(relations);
          const linked = new Set(relations.map((r) => r.loteId));
          this.lots.set((lots?.contenido ?? []).filter((l) => l.disponible || linked.has(l.id)));
          const chosen = relations.find((r) => r.seleccionado) ?? relations[0];
          this.lotId.set(chosen?.loteId ?? this.lots()[0]?.id ?? null);
          this.loading.set(false);
        });
        this.loadHistory(o.id);
      },
      error: (e) => {
        this.loading.set(false);
        this.toast.error(apiErrorMessage(e, 'No se encontró la oportunidad.'));
        this.changeOpportunity();
      },
    });
  }

  private loadHistory(opportunityId: number): void {
    this.crm
      .simulations({ opportunityId, size: 20 })
      .pipe(catchError(() => of(null)))
      .subscribe((page) => this.history.set(page?.contenido ?? []));
  }

  private reset(): void {
    this.opportunity.set(null);
    this.prospect.set(null);
    this.lots.set([]);
    this.relations.set([]);
    this.lotId.set(null);
    this.history.set([]);
  }
}
