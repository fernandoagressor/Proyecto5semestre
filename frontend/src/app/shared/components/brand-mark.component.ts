import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Marca de Nexora Technologies: emblema y, opcionalmente, el nombre al lado. */
@Component({
  selector: 'app-brand-mark',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <img class="brand-symbol" src="brand/logo-nexora-emblema.png" [alt]="showText() ? '' : 'Nexora'" width="46" height="46" />
    @if (showText()) {
      <span class="brand-copy">
        <strong>NEXORA</strong>
        <small>TECHNOLOGIES</small>
      </span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: .72rem; }
    .brand-symbol { display: block; width: 2.9rem; height: auto; }
    .brand-copy { display: grid; line-height: 1; letter-spacing: .12em; }
    strong { font-size: .92rem; font-weight: 800; }
    small { margin-top: .3rem; font-size: .58rem; font-weight: 700; letter-spacing: .3em; opacity: .7; }
  `,
})
export class BrandMarkComponent {
  readonly showText = input(true);
}
