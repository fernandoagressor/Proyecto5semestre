import { Periodicity } from '../../core/models/crm.models';

export const PERIODS_PER_YEAR: Record<Periodicity, number> = { MENSUAL: 12, BIMESTRAL: 6, TRIMESTRAL: 4, SEMESTRAL: 2, ANUAL: 1 };
const MONTHS_PER_PERIOD: Record<Periodicity, number> = { MENSUAL: 1, BIMESTRAL: 2, TRIMESTRAL: 3, SEMESTRAL: 6, ANUAL: 12 };

export interface PlanRow {
  numero: number;
  fecha: string;
  capital: number;
  interes: number;
  cuota: number;
  saldo: number;
}

export interface Plan {
  installment: number;
  financed: number;
  totalInterest: number;
  totalPaid: number;
  rows: PlanRow[];
}

/**
 * Cuota fija (sistema francés), igual que PlanCuotasService de la API: tasa del periodo
 * (1 + EA)^(1 / periodos al año) − 1. Es la vista previa; el plan oficial lo calcula la API al
 * guardar la simulación.
 */
export function frenchPlan(financed: number, installments: number, annualRate: number, periodicity: Periodicity, firstDate: string): Plan {
  const n = Math.max(1, Math.round(installments));
  const i = annualRate > 0 ? Math.pow(1 + annualRate, 1 / PERIODS_PER_YEAR[periodicity]) - 1 : 0;
  const installment = i === 0 ? financed / n : (financed * i) / (1 - Math.pow(1 + i, -n));
  const rows: PlanRow[] = [];
  let balance = financed;
  const start = firstDate ? new Date(`${firstDate}T00:00:00`) : new Date();
  for (let k = 1; k <= n; k++) {
    const interest = balance * i;
    let capital = installment - interest;
    if (k === n) capital = balance;
    balance = Math.max(0, balance - capital);
    const date = new Date(start.getFullYear(), start.getMonth() + (k - 1) * MONTHS_PER_PERIOD[periodicity], start.getDate());
    rows.push({ numero: k, fecha: date.toISOString().slice(0, 10), capital, interes: interest, cuota: capital + interest, saldo: balance });
  }
  const totalInterest = rows.reduce((s, r) => s + r.interes, 0);
  return { installment, financed, totalInterest, totalPaid: financed + totalInterest, rows };
}

/** Capital e intereses pagados por año calendario, para la gráfica. */
export function byYear(rows: { fecha: string; capital: number; interes: number }[]) {
  const map = new Map<string, { capital: number; interes: number }>();
  for (const r of rows) {
    const y = r.fecha.slice(0, 4);
    const acc = map.get(y) ?? { capital: 0, interes: 0 };
    acc.capital += r.capital;
    acc.interes += r.interes;
    map.set(y, acc);
  }
  return [...map.entries()].map(([year, v]) => ({ year, ...v, total: v.capital + v.interes }));
}
