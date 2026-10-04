import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ThemeService } from './core/services/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styles: ':host { display: contents; }',
})
export class App {
  private readonly theme = inject(ThemeService);

  constructor() {
    this.theme.initialize();
  }
}
