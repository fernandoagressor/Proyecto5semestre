import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiProblem } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, AppIconComponent, BrandMarkComponent],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Mensajes rotativos del panel de la izquierda (cada 6 s). */
  readonly slides = [
    { tag: 'Inventario', title: 'Cada lote con su estado, precio e historia.' },
    { tag: 'Ventas', title: 'De la reserva a la firma del contrato, sin papeles sueltos.' },
    { tag: 'Cartera', title: 'Cuotas, pagos y mora al día, con alertas a tiempo.' },
  ];
  readonly slide = signal(0);
  readonly loading = signal(false);
  readonly showPassword = signal(false);
  readonly apiError = signal<string | null>(null);
  readonly sessionExpired = computed(() => this.route.snapshot.queryParamMap.get('sesion') === 'expirada');
  readonly passwordUpdated = computed(() => this.route.snapshot.queryParamMap.get('password') === 'actualizada');

  readonly form = this.formBuilder.nonNullable.group({
    identificador: ['', [Validators.required, Validators.maxLength(180)]],
    password: ['', [Validators.required, Validators.maxLength(200)]],
  });

  constructor() {
    const timer = setInterval(() => this.slide.update((i) => (i + 1) % this.slides.length), 6000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  submit(): void {
    this.apiError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          const user = this.auth.user();
          const target = user?.requiereCambioPassword
            ? '/seguridad/cambiar-password'
            : user?.roles.includes('SUPER_ADMIN') && !user.empresaId
              ? '/superadmin'
              : this.safeReturnUrl();
          void this.router.navigateByUrl(target);
        },
        error: (error: HttpErrorResponse) => {
          const problem = error.error as ApiProblem | undefined;
          this.apiError.set(problem?.detail ?? 'No fue posible iniciar sesión. Inténtalo de nuevo.');
        },
      });
  }

  private safeReturnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return returnUrl?.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/dashboard';
  }
}
