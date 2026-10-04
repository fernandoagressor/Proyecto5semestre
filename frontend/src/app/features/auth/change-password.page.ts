import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiProblem } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { AppIconComponent } from '../../shared/components/app-icon.component';

const STRONG_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])\S+$/;

@Component({
  selector: 'app-change-password-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AppIconComponent,
  ],
  templateUrl: './change-password.page.html',
  styleUrl: './change-password.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePasswordPage {
  private readonly formBuilder = inject(FormBuilder);
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly apiError = signal<string | null>(null);
  readonly showCurrent = signal(false);
  readonly showNew = signal(false);
  readonly showConfirmation = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    passwordActual: ['', [Validators.required, Validators.maxLength(72)]],
    passwordNueva: [
      '',
      [Validators.required, Validators.minLength(12), Validators.maxLength(72), Validators.pattern(STRONG_PASSWORD)],
    ],
    confirmacionPassword: ['', [Validators.required, Validators.maxLength(72)]],
  });

  submit(): void {
    this.apiError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.form.getRawValue();
    if (payload.passwordNueva !== payload.confirmacionPassword) {
      this.form.controls.confirmacionPassword.setErrors({ mismatch: true });
      return;
    }

    this.loading.set(true);
    this.auth
      .changePassword(payload)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          this.auth.clearSession();
          void this.router.navigate(['/login'], { queryParams: { password: 'actualizada' } });
        },
        error: (error: HttpErrorResponse) => {
          const problem = error.error as ApiProblem | undefined;
          const fieldMessage = problem?.errores ? Object.values(problem.errores)[0] : undefined;
          this.apiError.set(
            fieldMessage ?? problem?.detail ?? 'No fue posible cambiar la contraseña. Inténtalo de nuevo.',
          );
        },
      });
  }

  passwordRule(rule: 'length' | 'upper' | 'lower' | 'number' | 'symbol'): boolean {
    const value = this.form.controls.passwordNueva.value;
    const checks = {
      length: value.length >= 12 && value.length <= 72,
      upper: /[A-Z]/.test(value),
      lower: /[a-z]/.test(value),
      number: /\d/.test(value),
      symbol: /[^A-Za-z0-9\s]/.test(value),
    };
    return checks[rule];
  }
}
