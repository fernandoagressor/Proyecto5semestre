import { DestroyRef, Injectable, NgZone, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Minutos sin usar la aplicación antes de cerrar la sesión. */
export const MINUTOS_INACTIVIDAD = 30;
const LIMITE_MS = MINUTOS_INACTIVIDAD * 60_000;
/** El aviso "tu sesión se va a cerrar" aparece este tiempo antes. */
const AVISO_MS = 60_000;
const CLAVE = 'nexora.ultima-actividad';
const EVENTOS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;

/**
 * Cierra la sesión después de {@link MINUTOS_INACTIVIDAD} minutos sin actividad (mouse, teclado,
 * toques). La última actividad se comparte entre pestañas por localStorage, así que trabajar en una
 * pestaña mantiene viva la sesión en las demás; al volver a abrir el navegador después del límite,
 * la sesión ya no se retoma. Un minuto antes se muestra un aviso para seguir conectado.
 */
@Injectable({ providedIn: 'root' })
export class InactivityService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);

  /** Segundos que faltan para cerrar la sesión, o null si no hay aviso. */
  readonly secondsLeft = signal<number | null>(null);

  private memoria = 0;
  private ultimaEscritura = 0;
  private iniciado = false;

  constructor() {
    // Al iniciar sesión (o cambiar de empresa) la cuenta empieza de cero.
    let autenticado = this.auth.isAuthenticated();
    effect(() => {
      const ahora = this.auth.isAuthenticated();
      if (ahora && !autenticado) this.touch(true);
      if (!ahora) this.secondsLeft.set(null);
      autenticado = ahora;
    });
  }

  start(): void {
    if (this.iniciado || typeof window === 'undefined') return;
    this.iniciado = true;
    if (this.auth.isAuthenticated() && this.ultimaActividad() === 0) this.touch(true);
    this.check();

    this.zone.runOutsideAngular(() => {
      const registrar = () => this.touch(false);
      EVENTOS.forEach((evento) => document.addEventListener(evento, registrar, { passive: true, capture: true }));
      const intervalo = window.setInterval(() => this.zone.run(() => this.check()), 5_000);
      this.destroyRef.onDestroy(() => {
        EVENTOS.forEach((evento) => document.removeEventListener(evento, registrar, { capture: true }));
        window.clearInterval(intervalo);
      });
    });
  }

  /** Botón "Seguir conectado" del aviso. */
  stayConnected(): void {
    this.touch(true);
    this.secondsLeft.set(null);
  }

  private check(): void {
    if (!this.auth.isAuthenticated()) return;
    const inactivo = Date.now() - this.ultimaActividad();
    if (inactivo >= LIMITE_MS) {
      this.expire();
    } else if (inactivo >= LIMITE_MS - AVISO_MS) {
      this.secondsLeft.set(Math.max(1, Math.ceil((LIMITE_MS - inactivo) / 1000)));
    } else if (this.secondsLeft() !== null) {
      this.secondsLeft.set(null);
    }
  }

  private expire(): void {
    this.secondsLeft.set(null);
    const request = this.auth.logoutRequest();
    this.auth.clearSession();
    request?.subscribe({ error: () => undefined });
    void this.router.navigate(['/login'], { queryParams: { sesion: 'inactiva' } });
  }

  /** Guarda la actividad; los eventos seguidos se escriben a lo sumo cada 10 s. */
  private touch(forzar: boolean): void {
    const ahora = Date.now();
    if (!forzar && ahora - this.ultimaEscritura < 10_000) return;
    this.ultimaEscritura = ahora;
    this.memoria = ahora;
    try {
      localStorage.setItem(CLAVE, String(ahora));
    } catch {
      // Sin almacenamiento (modo privado estricto): se usa solo la memoria de esta pestaña.
    }
  }

  private ultimaActividad(): number {
    let guardada = 0;
    try {
      guardada = Number(localStorage.getItem(CLAVE)) || 0;
    } catch {
      guardada = 0;
    }
    return Math.max(guardada, this.memoria);
  }
}
