import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Encabezado de pantalla del rediseño: etiqueta superior en índigo, título en Manrope y, a la
 * derecha, las acciones que se proyecten (botones, filtros).
 *
 *   <nx-page-header eyebrow="Control" title="Auditoría y alertas" description="...">
 *     <button class="nx-btn">Exportar</button>
 *   </nx-page-header>
 */
@Component({
  selector: 'nx-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="copy">
      @if (eyebrow()) {
        <span class="nx-eyebrow">{{ eyebrow() }}</span>
      }
      <h1>{{ title() }}</h1>
      @if (description()) {
        <p>{{ description() }}</p>
      }
    </div>
    <div class="actions"><ng-content /></div>
  `,
  styles: `
    :host { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 16px; }
    .copy { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
    h1 { margin: 0; color: var(--text-strong); font-family: var(--font-display); font-size: 30px; font-weight: 800; line-height: 36px; letter-spacing: -.02em; }
    p { max-width: 70ch; margin: 0; color: var(--text-muted); font-size: 14px; }
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .actions:empty { display: none; }
    @media (max-width: 560px) { h1 { font-size: 24px; line-height: 30px; } }
  `,
})
export class PageHeaderComponent {
  readonly eyebrow = input<string>('');
  readonly title = input.required<string>();
  readonly description = input<string>('');
}
