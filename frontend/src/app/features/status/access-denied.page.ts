import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppIconComponent } from '../../shared/components/app-icon.component';

@Component({
  selector: 'app-access-denied',
  imports: [RouterLink, AppIconComponent],
  template: `<section class="status"><span><app-icon name="audit" /></span><small>ACCESO RESTRINGIDO</small><h2>Este módulo no está asignado a tu rol</h2><p>Nexora Core protege cada operación con permisos específicos. Si necesitas acceso, solicítalo al administrador de tu empresa.</p><a routerLink="/dashboard"><app-icon name="arrow" /> Volver al dashboard</a></section>`,
  styles: `.status{min-height:65dvh;display:grid;place-items:center;align-content:center;text-align:center}.status>span{display:grid;place-items:center;width:5rem;height:5rem;margin-bottom:1rem;border-radius:1.4rem;color:var(--color-primary);background:var(--color-primary-soft)}.status>span app-icon{width:2.2rem;height:2.2rem}.status small{color:var(--color-primary);font-size:.58rem;font-weight:800;letter-spacing:.16em}.status h2{max-width:34rem;margin:.7rem 0;color:var(--text-strong);font-size:clamp(1.8rem,4vw,3rem);letter-spacing:-.045em}.status p{max-width:33rem;margin:0;color:var(--text-muted);font-size:.72rem;line-height:1.7}.status a{display:flex;align-items:center;gap:.45rem;margin-top:1.5rem;padding:.7rem .9rem;border-radius:.7rem;color:#fff;background:var(--color-primary);text-decoration:none;font-size:.65rem;font-weight:700}.status a app-icon{width:.85rem;height:.85rem}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessDeniedPage {}
