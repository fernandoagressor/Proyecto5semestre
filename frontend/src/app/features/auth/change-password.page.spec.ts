import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../core/services/auth.service';
import { ChangePasswordPage, passwordStrength } from './change-password.page';

function setup(roles: string[]) {
  const auth = {
    user: signal({ id: 9, nombreUsuario: 'Laura', roles, requiereCambioPassword: true }),
    changePassword: vi.fn(() => of(undefined)),
    clearSession: vi.fn(),
  };
  TestBed.configureTestingModule({
    imports: [ChangePasswordPage],
    providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
  });
  const fixture = TestBed.createComponent(ChangePasswordPage);
  fixture.detectChanges();
  return { fixture, auth, el: fixture.nativeElement as HTMLElement };
}

function type(el: HTMLElement, id: string, value: string) {
  const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('ChangePasswordPage', () => {
  it('shows the client portal first access with the Nexora brand and portal sections', () => {
    const { el } = setup(['CLIENTE']);

    expect(el.querySelector('.brand__mark')?.getAttribute('src')).toBe('brand/logo-nexora-emblema.png');
    expect(el.querySelector('h1')?.textContent).toContain('Portal del Cliente');
    expect([...el.querySelectorAll('.tiles b')].map((b) => b.textContent)).toEqual(['Lotes', 'Reservas', 'Pagos', 'Documentos']);
    expect(el.querySelector('label[for="passwordActual"]')?.textContent).toBe('Contraseña actual');
    expect(el.querySelector('.steps')).toBeNull();
  });

  it('opens and closes the help panel', () => {
    const { fixture, el } = setup(['CLIENTE']);
    const help = [...el.querySelectorAll<HTMLButtonElement>('.access__pill')].find((b) => b.textContent?.includes('ayuda'))!;

    help.click();
    fixture.detectChanges();
    expect(el.querySelector('.help__panel')?.textContent).toContain('asesor');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(el.querySelector('.help__panel')).toBeNull();
  });

  it('activates the new password, closes the session and returns to login', async () => {
    const { fixture, auth, el } = setup(['CLIENTE']);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    type(el, 'passwordActual', 'Temporal-2026');
    type(el, 'passwordNueva', 'Mar-Azul-2026!');
    type(el, 'confirmacionPassword', 'Mar-Azul-2026!');
    fixture.detectChanges();

    expect(el.querySelectorAll('.rules li.met').length).toBe(5);
    expect(el.querySelectorAll('.strength__bars i.on').length).toBeGreaterThanOrEqual(4);
    expect(el.querySelector('.field__ok')?.textContent).toContain('coinciden');

    el.querySelector<HTMLButtonElement>('.submit')!.click();

    expect(auth.changePassword).toHaveBeenCalledWith({
      passwordActual: 'Temporal-2026',
      passwordNueva: 'Mar-Azul-2026!',
      confirmacionPassword: 'Mar-Azul-2026!',
    });
    expect(auth.clearSession).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { password: 'actualizada' } });
  });

  it('does not submit when the confirmation differs', () => {
    const { fixture, auth, el } = setup(['ASESOR_COMERCIAL']);

    type(el, 'passwordActual', 'Temporal-2026');
    type(el, 'passwordNueva', 'Mar-Azul-2026!');
    type(el, 'confirmacionPassword', 'Mar-Azul-2026?');
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('.submit')!.click();
    fixture.detectChanges();

    expect(auth.changePassword).not.toHaveBeenCalled();
    expect(el.querySelector('.access__top')).toBeNull();
    expect(el.querySelector('.field__error')?.textContent).toContain('no coinciden');
  });

  it('scores weak and strong passwords', () => {
    expect(passwordStrength('abc').score).toBe(0);
    expect(passwordStrength('Mar-Azul-2026!').score).toBeGreaterThanOrEqual(3);
  });
});
