import { ChangeDetectionStrategy, Component, inject, Injectable, signal } from '@angular/core';

import { AppIconComponent } from '../components/app-icon.component';

export type ToastTone = 'success' | 'danger' | 'info';

export interface Toast {
  id: number;
  tone: ToastTone;
  text: string;
}

/**
 * Mensajes breves de resultado ("Meta guardada", "No se pudo aprobar…"). El contenedor
 * `<nx-toaster />` vive en el shell; las pantallas solo llaman a `toast.success(...)`.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private next = 1;
  readonly toasts = signal<Toast[]>([]);

  success(text: string): void { this.show('success', text); }
  error(text: string): void { this.show('danger', text, 7000); }
  info(text: string): void { this.show('info', text); }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private show(tone: ToastTone, text: string, ms = 4000): void {
    const id = this.next++;
    this.toasts.update((list) => [...list.slice(-3), { id, tone, text }]);
    setTimeout(() => this.dismiss(id), ms);
  }
}

@Component({
  selector: 'nx-toaster',
  imports: [AppIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="item" [attr.data-tone]="t.tone" [attr.role]="t.tone === 'danger' ? 'alert' : 'status'">
          <app-icon [name]="t.tone === 'success' ? 'check' : t.tone === 'danger' ? 'warning' : 'info'" />
          <span>{{ t.text }}</span>
          <button type="button" (click)="toast.dismiss(t.id)" aria-label="Cerrar mensaje"><app-icon name="close" /></button>
        </div>
      }
    </div>
  `,
  styles: `
    .stack { position: fixed; right: 20px; bottom: 20px; z-index: 120; display: flex; flex-direction: column; gap: 8px; width: min(380px, calc(100vw - 32px)); }
    .item { display: flex; align-items: flex-start; gap: 10px; padding: 12px 12px 12px 14px; border: 1px solid var(--border-soft); border-radius: 12px; color: var(--text-strong); background: var(--surface-card); box-shadow: var(--shadow-xl); font-size: 14px; animation: in .2s ease; }
    .item > app-icon { width: 18px; height: 18px; margin-top: 1px; }
    .item[data-tone='success'] > app-icon { color: var(--tone-success-fg); }
    .item[data-tone='danger'] > app-icon { color: var(--tone-danger-fg); }
    .item[data-tone='info'] > app-icon { color: var(--color-primary); }
    span { flex: 1; line-height: 1.4; }
    button { display: grid; place-items: center; width: 24px; height: 24px; padding: 0; border: 0; border-radius: 6px; color: var(--text-subtle); background: transparent; cursor: pointer; }
    button app-icon { width: 14px; height: 14px; }
    @keyframes in { from { opacity: 0; transform: translateY(8px); } }
  `,
})
export class ToasterComponent {
  readonly toast = inject(ToastService);
}

/** Mensaje legible de un error HTTP de la API (ProblemDetail) o el texto por defecto. */
export function apiErrorMessage(error: unknown, fallback = 'No fue posible completar la operación.'): string {
  const e = error as { status?: number; error?: { detail?: string; title?: string; errores?: Record<string, string> } } | null;
  if (e?.status === 0) return 'No hay conexión con el servidor. Revisa tu red e inténtalo de nuevo.';
  const fields = e?.error?.errores ? Object.values(e.error.errores) : [];
  return fields[0] ?? e?.error?.detail ?? e?.error?.title ?? fallback;
}
