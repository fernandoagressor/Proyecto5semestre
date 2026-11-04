import { HttpErrorResponse } from '@angular/common/http';
import { afterNextRender, ChangeDetectionStrategy, Component, computed, ElementRef, inject, Injector, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiProblem } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { homeUrl } from '../../core/guards/client-portal.guard';
import { AppIconComponent } from '../../shared/components/app-icon.component';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, AppIconComponent],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly errorAlert = viewChild<ElementRef<HTMLElement>>('errorAlert');

  readonly loading = signal(false);
  readonly showPassword = signal(false);
  /** Muestra a quién pedirle el restablecimiento; no existe un flujo de recuperación propio. */
  readonly showHelp = signal(false);
  readonly apiError = signal<string | null>(null);
  readonly sessionExpired = computed(() => this.route.snapshot.queryParamMap.get('sesion') === 'expirada');
  readonly passwordUpdated = computed(() => this.route.snapshot.queryParamMap.get('password') === 'actualizada');

  readonly form = this.formBuilder.nonNullable.group({
    identificador: ['', [Validators.required, Validators.maxLength(180)]],
    password: ['', [Validators.required, Validators.maxLength(200)]],
  });

  identificadorInvalid(): boolean {
    const control = this.form.controls.identificador;
    return control.touched && control.hasError('required');
  }

  passwordInvalid(): boolean {
    const control = this.form.controls.password;
    return control.touched && control.hasError('required');
  }

  submit(): void {
    this.apiError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Lleva el foco al primer campo con error para que teclado y lector de pantalla lo encuentren.
      this.host.nativeElement.querySelector<HTMLInputElement>('input.ng-invalid')?.focus();
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
            ? homeUrl(this.auth)
            : user?.roles.includes('SUPER_ADMIN') && !user.empresaId
              ? '/superadmin'
              : user?.roles.includes('CLIENTE')
                ? '/portal'
                : this.safeReturnUrl();
          void this.router.navigateByUrl(target);
        },
        error: (error: HttpErrorResponse) => {
          const problem = error.error as ApiProblem | undefined;
          this.apiError.set(problem?.detail ?? 'No fue posible iniciar sesión. Inténtalo de nuevo.');
          afterNextRender(() => this.errorAlert()?.nativeElement.focus(), { injector: this.injector });
        },
      });
  }

  private safeReturnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return returnUrl?.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/dashboard';
  }
}
