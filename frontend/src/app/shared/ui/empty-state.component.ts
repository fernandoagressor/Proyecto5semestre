import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { AppIconComponent } from '../components/app-icon.component';

/**
 * Estado de una sección: cargando, error (con reintentar) o vacío. Cuando no aplica ninguno,
 * muestra el contenido proyectado.
 *
 *   <nx-state [loading]="cargando()" [error]="error()" [empty]="filas().length === 0"
 *             emptyTitle="Sin pagos este mes" (retry)="cargar()">
 *     <table class="nx-table">...</table>
 *   </nx-state>
 */
@Component({
  selector: 'nx-state',
  imports: [AppIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="skeletons" aria-busy="true" aria-label="Cargando">
        @for (row of skeletonRows; track row) {
          <span class="nx-skeleton" [style.width.%]="row"></span>
        }
      </div>
    } @else if (error()) {
      <div class="message error" role="alert">
        <app-icon name="warning" />
        <strong>No se pudo cargar</strong>
        <span>{{ error() }}</span>
        <button type="button" class="nx-btn nx-btn--sm" (click)="retry.emit()">
          <app-icon name="refresh" /> Reintentar
        </button>
      </div>
    } @else if (empty()) {
      <div class="message">
        <app-icon [name]="emptyIcon()" />
        <strong>{{ emptyTitle() }}</strong>
        @if (emptyText()) {
          <span>{{ emptyText() }}</span>
        }
        <ng-content select="[empty-action]" />
      </div>
    } @else {
      <ng-content />
    }
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .skeletons { display: grid; gap: 12px; padding: 8px 0; }
    .skeletons .nx-skeleton { height: 18px; }
    .message { display: grid; justify-items: center; gap: 6px; padding: 32px 16px; color: var(--text-muted); font-size: 14px; text-align: center; }
    .message app-icon { width: 28px; height: 28px; margin-bottom: 4px; color: var(--text-subtle); }
    .message strong { color: var(--text-strong); font-size: 15px; }
    .message span { max-width: 48ch; }
    .message button { margin-top: 8px; }
    .error app-icon { color: var(--tone-danger-fg); }
  `,
})
export class EmptyStateComponent {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly emptyTitle = input('No hay registros');
  readonly emptyText = input('');
  readonly emptyIcon = input('search');
  readonly retry = output<void>();

  readonly skeletonRows = [92, 78, 85, 64];
}
