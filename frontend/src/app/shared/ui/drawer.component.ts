import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  model,
  viewChild,
} from '@angular/core';

import { AppIconComponent } from '../components/app-icon.component';

/**
 * Panel lateral (detalle de un registro, formulario de edición) o diálogo centrado
 * (`variant="dialog"`, para confirmaciones). Se cierra con Esc, con la X o tocando el fondo
 * (salvo `[dismissible]="false"` mientras se guarda). Devuelve el foco a donde estaba.
 *
 *   <nx-drawer [(open)]="detalleAbierto" title="Contrato VTA-2026-0141" subtitle="Lote M03-L08">
 *     ...contenido...
 *     <ng-container drawer-footer>
 *       <button class="nx-btn" (click)="detalleAbierto.set(false)">Cerrar</button>
 *     </ng-container>
 *   </nx-drawer>
 */
@Component({
  selector: 'nx-drawer',
  imports: [AppIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div class="backdrop" (click)="dismiss()"></div>
      <section
        #panel
        class="panel"
        [class.dialog]="variant() === 'dialog'"
        [style.--panel-width]="width()"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="title()"
        tabindex="-1"
        (keydown.escape)="dismiss()"
      >
        <header>
          <div>
            <h2>{{ title() }}</h2>
            @if (subtitle()) {
              <p>{{ subtitle() }}</p>
            }
          </div>
          <button type="button" class="close" (click)="dismiss()" [disabled]="!dismissible()" aria-label="Cerrar">
            <app-icon name="close" />
          </button>
        </header>
        <div class="body"><ng-content /></div>
        <footer><ng-content select="[drawer-footer]" /></footer>
      </section>
    }
  `,
  styles: `
    .backdrop { position: fixed; inset: 0; z-index: 90; background: rgba(7, 20, 38, .38); backdrop-filter: blur(2px); animation: fade .18s ease; }
    .panel { position: fixed; top: 0; right: 0; bottom: 0; z-index: 91; display: flex; flex-direction: column; width: min(var(--panel-width, 520px), 100vw); border-left: 1px solid var(--border-soft); background: var(--surface-card); box-shadow: var(--shadow-xl); outline: 0; animation: slide .22s ease; }
    .panel.dialog { top: 50%; right: auto; bottom: auto; left: 50%; width: min(var(--panel-width, 480px), calc(100vw - 32px)); max-height: calc(100dvh - 48px); border: 1px solid var(--border-soft); border-radius: var(--radius-card); transform: translate(-50%, -50%); animation: pop .18s ease; }
    header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 20px 22px 14px; border-bottom: 1px solid var(--border-faint); }
    h2 { margin: 0; color: var(--text-strong); font-size: 19px; font-weight: 800; line-height: 1.3; }
    header p { margin: 4px 0 0; color: var(--text-muted); font-size: 13px; }
    .close { display: grid; place-items: center; width: 36px; height: 36px; flex: none; padding: 0; border: 1px solid var(--border-soft); border-radius: 9px; color: var(--text-muted); background: var(--surface-card); cursor: pointer; }
    .close:hover:not(:disabled) { color: var(--text-strong); border-color: var(--border-medium); }
    .close app-icon { width: 16px; height: 16px; }
    .body { flex: 1; overflow-y: auto; padding: 18px 22px; }
    footer { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; padding: 14px 22px; border-top: 1px solid var(--border-faint); }
    footer:empty { display: none; }
    @keyframes fade { from { opacity: 0; } }
    @keyframes slide { from { transform: translateX(24px); opacity: 0; } }
    @keyframes pop { from { transform: translate(-50%, -48%); opacity: 0; } }
  `,
})
export class DrawerComponent {
  readonly open = model(false);
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly variant = input<'drawer' | 'dialog'>('drawer');
  /** Ancho CSS del panel, por ejemplo '640px'. */
  readonly width = input<string | null>(null);
  readonly dismissible = input(true);

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly document = inject(ElementRef).nativeElement.ownerDocument as Document;
  private previousFocus: HTMLElement | null = null;
  private locked = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.locked) this.document.body.style.overflow = '';
    });
    effect(() => {
      const panel = this.panel();
      if (this.open() && panel && !this.locked) {
        this.locked = true;
        this.previousFocus = this.document.activeElement as HTMLElement | null;
        this.document.body.style.overflow = 'hidden';
        queueMicrotask(() => panel.nativeElement.focus());
      } else if (!this.open() && this.locked) {
        this.locked = false;
        this.document.body.style.overflow = '';
        this.previousFocus?.focus?.();
        this.previousFocus = null;
      }
    });
  }

  dismiss(): void {
    if (this.dismissible()) this.open.set(false);
  }
}
