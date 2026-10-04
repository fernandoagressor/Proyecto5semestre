import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-brand-mark',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="brand-symbol" aria-hidden="true">
      <svg viewBox="0 0 44 44">
        <path d="M7 31.5V12.8L16.7 7v18.8L7 31.5Z" />
        <path d="M18.8 24.5V5.8L28.5 11v18.8l-9.7-5.3Z" />
        <path d="m30.6 12.2 6.4 3.5v18.6l-6.4 3.9v-26Z" />
        <path class="accent" d="m7 34 10.8-6.2 10.7 5.8L37 28.5v5.8l-8.5 5L17.8 33.5 7 39.7V34Z" />
      </svg>
    </span>
    @if (showText()) {
      <span class="brand-copy">
        <strong>NEXORA</strong>
        <small>CORE</small>
      </span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: .72rem; }
    .brand-symbol { display: grid; place-items: center; width: 2.45rem; height: 2.45rem; }
    svg { width: 100%; height: 100%; fill: currentColor; }
    .accent { fill: var(--color-accent, #38bdf8); }
    .brand-copy { display: grid; line-height: 1; letter-spacing: .13em; }
    strong { font-size: .97rem; font-weight: 800; }
    small { margin-top: .25rem; font-size: .55rem; letter-spacing: .36em; opacity: .62; }
  `,
})
export class BrandMarkComponent {
  readonly showText = input(true);
}
