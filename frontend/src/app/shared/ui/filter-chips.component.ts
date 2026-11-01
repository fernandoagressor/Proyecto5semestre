import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

export interface ChipOption {
  value: string;
  label: string;
  count?: number | null;
}

/**
 * Filtros rápidos en píldoras (selección única). El valor '' es "Todos".
 *
 *   <nx-filter-chips [options]="estados" [(value)]="estado" label="Estado del lote" />
 */
@Component({
  selector: 'nx-filter-chips',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chips" role="radiogroup" [attr.aria-label]="label()">
      @for (option of options(); track option.value) {
        <button type="button" role="radio" [attr.aria-checked]="value() === option.value" [class.on]="value() === option.value" (click)="value.set(option.value)">
          {{ option.label }}
          @if (option.count !== undefined && option.count !== null) {
            <span>{{ option.count }}</span>
          }
        </button>
      }
    </div>
  `,
  styles: `
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    button { display: inline-flex; align-items: center; gap: 6px; min-height: 34px; padding: 0 12px; border: 1px solid var(--border-medium); border-radius: 999px; color: var(--text-muted); background: var(--surface-card); font-size: 13px; font-weight: 600; cursor: pointer; }
    button:hover { color: var(--text-strong); border-color: var(--text-subtle); }
    button.on { color: var(--color-primary-strong); border-color: var(--color-primary); background: var(--color-primary-soft); }
    span { color: inherit; font-size: 12px; opacity: .75; }
  `,
})
export class FilterChipsComponent {
  readonly options = input.required<readonly ChipOption[]>();
  readonly value = model<string>('');
  readonly label = input('Filtro');
}
