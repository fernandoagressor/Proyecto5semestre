import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { InactivityService, MINUTOS_INACTIVIDAD } from './core/services/inactivity.service';
import { ThemeService } from './core/services/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styles: `
    :host { display: contents; }
    .idle { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; background: rgba(6, 21, 40, .45); }
    .idle__box { display: flex; flex-direction: column; gap: 12px; width: min(420px, 100%); padding: 22px; border-radius: 16px; color: var(--text-strong); background: var(--surface-card); box-shadow: 0 20px 50px rgba(0, 0, 0, .25); }
    .idle__box h2 { margin: 0; font-size: 18px; }
    .idle__box p { margin: 0; color: var(--text-muted); line-height: 1.5; }
    .idle__box b { color: var(--text-strong); font-variant-numeric: tabular-nums; }
    .idle__box button { align-self: flex-end; }
  `,
})
export class App {
  private readonly theme = inject(ThemeService);
  readonly inactivity = inject(InactivityService);
  readonly minutos = MINUTOS_INACTIVIDAD;

  constructor() {
    this.theme.initialize();
    this.inactivity.start();
  }
}
