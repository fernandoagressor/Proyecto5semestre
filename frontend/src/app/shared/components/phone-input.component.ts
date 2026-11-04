import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALUE_ACCESSOR, ValidationErrors } from '@angular/forms';

export const PHONE_PREFIXES = [
  { code: '+57', label: 'Colombia (+57)' },
  { code: '+1', label: 'EE. UU. / Canadá (+1)' },
  { code: '+52', label: 'México (+52)' },
  { code: '+51', label: 'Perú (+51)' },
  { code: '+593', label: 'Ecuador (+593)' },
  { code: '+58', label: 'Venezuela (+58)' },
  { code: '+507', label: 'Panamá (+507)' },
  { code: '+56', label: 'Chile (+56)' },
  { code: '+54', label: 'Argentina (+54)' },
  { code: '+34', label: 'España (+34)' },
] as const;

export const DEFAULT_PHONE_PREFIX = '+57';
export const PHONE_MAX_DIGITS = 10;
const PHONE_MIN_DIGITS = 7;

/** Separa un teléfono guardado ("+57 3001234567", "+573001234567", "300 123 4567") en prefijo y dígitos. */
export function splitPhone(value: string | null | undefined): { prefix: string; number: string } {
  const text = (value ?? '').trim();
  const spaced = text.match(/^\+(\d{1,4})[\s-]+(.*)$/);
  if (spaced) return { prefix: `+${spaced[1]}`, number: spaced[2].replace(/\D/g, '') };
  const joined = text.match(/^\+(\d+)$/);
  if (joined) {
    // Sin espacio: el prefijo conocido más largo con el que empiece.
    const code = PHONE_PREFIXES.map((p) => p.code.slice(1))
      .sort((x, y) => y.length - x.length)
      .find((c) => joined[1].startsWith(c));
    return code
      ? { prefix: `+${code}`, number: joined[1].slice(code.length) }
      : { prefix: DEFAULT_PHONE_PREFIX, number: joined[1] };
  }
  return { prefix: DEFAULT_PHONE_PREFIX, number: text.replace(/\D/g, '') };
}

/** Une prefijo y número en el texto que se guarda; sin número queda vacío. */
export function joinPhone(prefix: string, number: string): string {
  const digits = number.replace(/\D/g, '').slice(0, PHONE_MAX_DIGITS);
  return digits ? `${prefix} ${digits}` : '';
}

/** Teléfono opcional: si hay número, entre 7 y 10 dígitos. */
export function phoneValidator(control: AbstractControl<string | null>): ValidationErrors | null {
  const value = (control.value ?? '').trim();
  if (!value) return null;
  const digits = splitPhone(value).number.length;
  if (digits > PHONE_MAX_DIGITS) return { phone: { max: PHONE_MAX_DIGITS } };
  return digits >= PHONE_MIN_DIGITS ? null : { phone: { min: PHONE_MIN_DIGITS } };
}

/**
 * Campo de teléfono con el prefijo del país aparte y el número limitado a 10 dígitos.
 * Guarda un solo texto, "+57 3001234567", para que el backend no cambie.
 */
@Component({
  selector: 'nx-phone-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PhoneInputComponent), multi: true }],
  template: `
    <select class="nx-select" [value]="prefix()" (change)="setPrefix($any($event.target).value)" (blur)="touched()" [disabled]="disabled()" [attr.aria-label]="'Prefijo de ' + label()">
      @for (p of prefixes; track p.code) { <option [value]="p.code" [selected]="p.code === prefix()">{{ p.code }}</option> }
      @if (!known()) { <option [value]="prefix()" selected>{{ prefix() }}</option> }
    </select>
    <input class="nx-input" type="tel" inputmode="numeric" autocomplete="tel-national" [attr.maxlength]="max"
      [value]="number()" (input)="setNumber($any($event.target))" (blur)="touched()" [disabled]="disabled()"
      [placeholder]="placeholder()" [attr.aria-label]="label()" />
  `,
  styles: `
    :host { display: grid; grid-template-columns: minmax(5.5rem, 6.5rem) minmax(0, 1fr); gap: 8px; width: 100%; }
    select, input { width: 100%; min-width: 0; }
    :host(.ng-invalid.ng-touched) input { border-color: var(--tone-danger-fg); }
  `,
})
export class PhoneInputComponent implements ControlValueAccessor {
  readonly label = input('Teléfono');
  readonly placeholder = input('300 000 0000');
  readonly prefixes = PHONE_PREFIXES;
  readonly max = PHONE_MAX_DIGITS;

  readonly prefix = signal<string>(DEFAULT_PHONE_PREFIX);
  readonly number = signal('');
  readonly disabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  known(): boolean {
    return this.prefixes.some((p) => p.code === this.prefix());
  }

  writeValue(value: string | null): void {
    const { prefix, number } = splitPhone(value);
    this.prefix.set(prefix);
    this.number.set(number.slice(0, PHONE_MAX_DIGITS));
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  setPrefix(prefix: string): void {
    this.prefix.set(prefix);
    this.onChange(joinPhone(prefix, this.number()));
  }

  /** Solo dígitos y como máximo 10: lo demás se descarta mientras se escribe o se pega. */
  setNumber(field: HTMLInputElement): void {
    const digits = field.value.replace(/\D/g, '').slice(0, PHONE_MAX_DIGITS);
    if (field.value !== digits) field.value = digits;
    this.number.set(digits);
    this.onChange(joinPhone(this.prefix(), digits));
  }

  touched(): void {
    this.onTouched();
  }
}
