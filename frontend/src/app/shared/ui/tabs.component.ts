import { ChangeDetectionStrategy, Component, ElementRef, inject, input, model } from '@angular/core';

export interface TabItem {
  id: string;
  label: string;
  /** Número opcional al lado de la etiqueta (pendientes, alertas…). */
  count?: number | null;
  disabled?: boolean;
}

/**
 * Pestañas accesibles (role="tablist"; flechas, Inicio y Fin para moverse). El contenido lo pinta
 * la pantalla según `active`:
 *
 *   <nx-tabs [items]="tabs" [(active)]="tab" label="Secciones de cartera" />
 *   @switch (tab()) { @case ('cuotas') { ... } }
 */
@Component({
  selector: 'nx-tabs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="list" role="tablist" [attr.aria-label]="label()" (keydown)="onKey($event)">
      @for (item of items(); track item.id) {
        <button
          type="button"
          role="tab"
          [id]="'tab-' + item.id"
          [attr.aria-selected]="active() === item.id"
          [attr.tabindex]="active() === item.id ? 0 : -1"
          [disabled]="item.disabled"
          [class.on]="active() === item.id"
          (click)="select(item)"
        >
          {{ item.label }}
          @if (item.count !== undefined && item.count !== null) {
            <span class="count">{{ item.count }}</span>
          }
        </button>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .list { display: flex; gap: 4px; overflow-x: auto; border-bottom: 1px solid var(--border-soft); scrollbar-width: none; }
    .list::-webkit-scrollbar { display: none; }
    button { position: relative; display: inline-flex; align-items: center; gap: 6px; min-height: 42px; padding: 0 14px; border: 0; color: var(--text-muted); background: transparent; font-size: 14px; font-weight: 600; white-space: nowrap; cursor: pointer; }
    button:hover:not(:disabled) { color: var(--text-strong); }
    button.on { color: var(--color-primary-strong); }
    button.on::after { content: ''; position: absolute; right: 10px; bottom: -1px; left: 10px; height: 2px; border-radius: 2px 2px 0 0; background: var(--color-primary); }
    button:disabled { opacity: .45; }
    .count { min-width: 20px; padding: 1px 7px; border-radius: 999px; color: var(--text-muted); background: var(--surface-sunken); font-size: 12px; font-weight: 700; text-align: center; }
    button.on .count { color: var(--color-primary-strong); background: var(--color-primary-soft); }
  `,
})
export class TabsComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly items = input.required<readonly TabItem[]>();
  readonly active = model.required<string>();
  readonly label = input('Secciones');

  select(item: TabItem): void {
    if (!item.disabled) this.active.set(item.id);
  }

  onKey(event: KeyboardEvent): void {
    const enabled = this.items().filter((i) => !i.disabled);
    const index = enabled.findIndex((i) => i.id === this.active());
    let next = -1;
    if (event.key === 'ArrowRight') next = (index + 1) % enabled.length;
    else if (event.key === 'ArrowLeft') next = (index - 1 + enabled.length) % enabled.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = enabled.length - 1;
    if (next < 0) return;
    event.preventDefault();
    this.active.set(enabled[next].id);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLButtonElement>(`#tab-${enabled[next].id}`)?.focus());
  }
}
