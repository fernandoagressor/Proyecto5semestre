import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, BrandMarkComponent],
  template: `<main class="not-found"><app-brand-mark /><strong>404</strong><h1>La página que buscas no existe</h1><p>La dirección puede haber cambiado o no estar disponible para este espacio de trabajo.</p><a routerLink="/dashboard">Ir al inicio</a></main>`,
  styles: `.not-found{min-height:100dvh;display:grid;place-items:center;align-content:center;padding:2rem;text-align:center;background:var(--surface-page);color:var(--color-primary)}.not-found>strong{margin-top:2rem;font-size:clamp(5rem,14vw,10rem);line-height:.8;letter-spacing:-.08em;opacity:.12}.not-found h1{margin:1rem 0 .4rem;color:var(--text-strong);font-size:clamp(1.7rem,4vw,2.8rem);letter-spacing:-.04em}.not-found p{max-width:32rem;margin:0;color:var(--text-muted);font-size:.72rem;line-height:1.6}.not-found a{margin-top:1.4rem;padding:.7rem 1rem;border-radius:.7rem;color:#fff;background:var(--color-primary);text-decoration:none;font-size:.65rem;font-weight:700}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {}
