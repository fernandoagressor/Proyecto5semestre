import { Pipe, PipeTransform } from '@angular/core';

/*
 * Formatos de Colombia usados en todas las pantallas del rediseño.
 * $ 92.000.000 · $ 412 M · 31 oct 2026 · 31 oct 2026, 10:42
 */

const COP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const NUM = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });
const DATE = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' });
const DATE_TIME = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

type Numeric = number | string | null | undefined;

function toNumber(value: Numeric): number | null {
  if (value === null || value === undefined || value === '') return null;
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

/** Fechas "2026-10-31" se toman como día local, no como medianoche UTC (que sería el 30). */
function toDate(value: string | Date | null | undefined): Date | null {
  if (!value) return null;
  if (value instanceof Date) return value;
  const d = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatCop(value: Numeric): string {
  const n = toNumber(value);
  return n === null ? '—' : COP.format(n).replace(/ /g, ' ');
}

/** Montos grandes abreviados para indicadores: $ 1,2 mil M, $ 412 M, $ 85 mil. */
export function formatCopCompact(value: Numeric): string {
  const n = toNumber(value);
  if (n === null) return '—';
  const abs = Math.abs(n);
  const sign = n < 0 ? '-' : '';
  if (abs >= 1e9) return `${sign}$ ${NUM.format(abs / 1e9)} mil M`;
  if (abs >= 1e6) return `${sign}$ ${NUM.format(abs / 1e6)} M`;
  if (abs >= 1e3) return `${sign}$ ${NUM.format(abs / 1e3)} mil`;
  return formatCop(n);
}

export function formatNumber(value: Numeric): string {
  const n = toNumber(value);
  return n === null ? '—' : NUM.format(n);
}

export function formatPercent(value: Numeric, digits = 1): string {
  const n = toNumber(value);
  return n === null ? '—' : `${n.toLocaleString('es-CO', { maximumFractionDigits: digits })} %`;
}

export function formatDate(value: string | Date | null | undefined): string {
  const d = toDate(value);
  return d ? DATE.format(d).replace('.', '') : '—';
}

export function formatDateTime(value: string | Date | null | undefined): string {
  const d = toDate(value);
  return d ? DATE_TIME.format(d).replace('.', '') : '—';
}

/** "hace 5 min", "hace 2 h", "ayer", o la fecha. */
export function formatRelative(value: string | Date | null | undefined, now = new Date()): string {
  const d = toDate(value);
  if (!d) return '—';
  const minutes = Math.round((now.getTime() - d.getTime()) / 60000);
  if (minutes < 1) return 'ahora';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  if (hours < 48) return 'ayer';
  return formatDate(d);
}

export function initials(name: string | null | undefined): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() || '?';
}

@Pipe({ name: 'cop' })
export class CopPipe implements PipeTransform {
  transform(value: Numeric, compact: 'compact' | '' = ''): string {
    return compact ? formatCopCompact(value) : formatCop(value);
  }
}

@Pipe({ name: 'fecha' })
export class FechaPipe implements PipeTransform {
  transform(value: string | Date | null | undefined, mode: 'fecha' | 'hora' | 'relativa' = 'fecha'): string {
    if (mode === 'hora') return formatDateTime(value);
    if (mode === 'relativa') return formatRelative(value);
    return formatDate(value);
  }
}

@Pipe({ name: 'porcentaje' })
export class PorcentajePipe implements PipeTransform {
  transform(value: Numeric, digits = 1): string {
    return formatPercent(value, digits);
  }
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Fecha local de hoy como AAAA-MM-DD (lo que esperan los LocalDate de la API). */
export function todayIso(date = new Date()): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Fecha y hora local como AAAA-MM-DDTHH:mm:ss (LocalDateTime de la API). */
export function localDateTimeIso(date: Date): string {
  return `${todayIso(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/** Mes actual como AAAA-MM. */
export function currentPeriod(date = new Date()): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
}

/** "2026-10" → "octubre 2026". */
export function periodLabel(period: string | null | undefined): string {
  if (!period || !/^\d{4}-\d{2}$/.test(period)) return period ?? '—';
  const [y, m] = period.split('-').map(Number);
  return new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' }).format(new Date(y, m - 1, 1)).replace(' de ', ' ');
}
