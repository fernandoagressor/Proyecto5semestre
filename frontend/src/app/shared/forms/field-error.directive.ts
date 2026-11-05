import { AfterViewInit, DestroyRef, Directive, ElementRef, Renderer2, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormGroupDirective, NgControl, ValidationErrors } from '@angular/forms';
import { EMPTY, merge } from 'rxjs';

let siguienteId = 1;
const numero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });

/**
 * Muestra debajo de cada campo de un formulario reactivo por qué no es válido.
 *
 * Se activa sola en todo control con `formControlName` o `formControl` que viva dentro de un
 * `.nx-field` (o de un `<label>`): cuando el campo es inválido y ya se tocó, o se intentó enviar el
 * formulario, agrega un `<small class="nx-field__error">` con el mensaje y marca el control con
 * `aria-invalid`. Los mensajes salen de los validadores (`required`, `min`, `maxlength`, `email`…) y
 * del error `server`, que pone `aplicarErroresDelServidor` con lo que respondió la API.
 *
 * Si la pantalla ya muestra su propio mensaje (otro `.nx-field__error` en el mismo campo), este se
 * oculta para no repetirlo. Para apagarlo en un campo: atributo `nxSinMensaje`.
 */
@Directive({ selector: '[formControlName]:not([nxSinMensaje]),[formControl]:not([nxSinMensaje])' })
export class FieldErrorDirective implements AfterViewInit {
  private readonly ngControl = inject(NgControl, { self: true, optional: true });
  private readonly formulario = inject(FormGroupDirective, { optional: true });
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);
  private readonly destroyRef = inject(DestroyRef);
  private mensaje: HTMLElement | null = null;
  private readonly id = `nx-error-${siguienteId++}`;

  ngAfterViewInit(): void {
    const control = this.ngControl?.control;
    if (!control) return;
    merge(control.events, this.formulario?.ngSubmit ?? EMPTY)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.actualizar(control));
    this.actualizar(control);
  }

  private actualizar(control: AbstractControl): void {
    const elemento = this.host.nativeElement;
    const visible = control.invalid && (control.touched || !!this.formulario?.submitted);
    const texto = visible ? mensajeDeError(control.errors, elemento) : null;
    const contenedor = elemento.closest<HTMLElement>('.nx-field') ?? elemento.closest<HTMLElement>('label');
    const propio = contenedor?.querySelector(`.nx-field__error:not(#${this.id})`);

    if (!texto || !contenedor || propio) {
      this.quitar(elemento);
      if (texto) this.renderer.setAttribute(elemento, 'aria-invalid', 'true');
      return;
    }
    if (!this.mensaje) {
      this.mensaje = this.renderer.createElement('small');
      this.renderer.addClass(this.mensaje, 'nx-field__error');
      this.renderer.setAttribute(this.mensaje, 'id', this.id);
      this.renderer.setAttribute(this.mensaje, 'role', 'alert');
      this.renderer.appendChild(contenedor, this.mensaje);
    }
    this.renderer.setProperty(this.mensaje, 'textContent', texto);
    this.renderer.setAttribute(elemento, 'aria-invalid', 'true');
    this.renderer.setAttribute(elemento, 'aria-describedby', this.id);
  }

  private quitar(elemento: HTMLElement): void {
    this.renderer.removeAttribute(elemento, 'aria-invalid');
    this.renderer.removeAttribute(elemento, 'aria-describedby');
    if (this.mensaje) {
      this.renderer.removeChild(this.mensaje.parentNode, this.mensaje);
      this.mensaje = null;
    }
  }
}

/** Texto en español para el primer error del control. */
export function mensajeDeError(errores: ValidationErrors | null, elemento?: HTMLElement): string | null {
  if (!errores) return null;
  const esLista = elemento?.tagName === 'SELECT';
  const esCasilla = elemento instanceof HTMLInputElement && elemento.type === 'checkbox';
  const [clave, valor] = Object.entries(errores)[0];
  switch (clave) {
    case 'server':
      return String(valor);
    case 'required':
      if (esCasilla) return 'Debes marcar esta casilla para continuar.';
      return esLista ? 'Selecciona una opción.' : 'Este campo es obligatorio.';
    case 'min':
      if (esLista) return 'Selecciona una opción.';
      return valor.min > 0 && valor.min < 1 ? 'Debe ser mayor que cero.' : `Debe ser mayor o igual a ${numero.format(valor.min)}.`;
    case 'max':
      return `Debe ser menor o igual a ${numero.format(valor.max)}.`;
    case 'minlength':
      return `Escribe al menos ${valor.requiredLength} caracteres.`;
    case 'maxlength':
      return `Máximo ${valor.requiredLength} caracteres (llevas ${valor.actualLength}).`;
    case 'email':
      return 'Escribe un correo válido, por ejemplo nombre@dominio.com.';
    case 'pattern':
      return 'El formato no es válido.';
    default:
      return typeof valor === 'string' ? valor : 'Revisa este campo.';
  }
}
