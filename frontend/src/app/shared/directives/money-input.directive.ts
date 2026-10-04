import { Directive, ElementRef, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Directive({
  selector: 'input[nexoraMoney]',
  standalone: true,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MoneyInputDirective),
      multi: true,
    },
  ],
  host: {
    '[attr.inputmode]': '"numeric"',
    '[attr.autocomplete]': '"off"',
    '(input)': 'handleInput($event)',
    '(blur)': 'handleBlur()',
  },
})
export class MoneyInputDirective implements ControlValueAccessor {
  private readonly element = inject<ElementRef<HTMLInputElement>>(ElementRef);
  private readonly formatter = new Intl.NumberFormat('es-CO', {
    maximumFractionDigits: 0,
  });

  private onChange: (value: number | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: number | null | undefined): void {
    this.element.nativeElement.value = this.format(value);
  }

  registerOnChange(callback: (value: number | null) => void): void {
    this.onChange = callback;
  }

  registerOnTouched(callback: () => void): void {
    this.onTouched = callback;
  }

  setDisabledState(disabled: boolean): void {
    this.element.nativeElement.disabled = disabled;
  }

  handleInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const cursor = input.selectionStart ?? input.value.length;
    const digitsBeforeCursor = input.value.slice(0, cursor).replace(/\D/g, '').length;
    const digits = input.value.replace(/\D/g, '');
    const value = digits ? Number(digits) : null;

    input.value = this.format(value);
    this.onChange(value);

    let nextCursor = 0;
    let digitsSeen = 0;
    while (nextCursor < input.value.length && digitsSeen < digitsBeforeCursor) {
      if (/\d/.test(input.value[nextCursor])) digitsSeen += 1;
      nextCursor += 1;
    }
    input.setSelectionRange(nextCursor, nextCursor);
  }

  handleBlur(): void {
    this.onTouched();
  }

  private format(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(Number(value))) return '';
    return this.formatter.format(Math.trunc(Number(value)));
  }
}
