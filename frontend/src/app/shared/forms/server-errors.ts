import { AbstractControl } from '@angular/forms';

interface ProblemaApi {
  errores?: Record<string, string>;
}

/**
 * Pone en cada campo del formulario el mensaje que devolvió la API para ese campo (respuesta 400
 * con `errores: { campo: mensaje }`), para que se vea debajo del campo y no solo arriba. El error
 * se borra solo cuando el usuario cambia el valor. Devuelve `true` si ubicó al menos un campo.
 */
export function aplicarErroresDelServidor(formulario: AbstractControl, error: unknown): boolean {
  const errores = (error as { error?: ProblemaApi } | null)?.error?.errores;
  if (!errores) return false;
  let ubicados = 0;
  for (const [campo, mensaje] of Object.entries(errores)) {
    const control = formulario.get(campo);
    if (!control) continue;
    control.setErrors({ ...(control.errors ?? {}), server: mensaje });
    control.markAsTouched();
    ubicados++;
  }
  return ubicados > 0;
}

/** `valorLista` → "Valor lista"; `cliente.correo` → "Cliente correo". */
export function etiquetaDeCampo(campo: string): string {
  const texto = campo
    .replace(/\[\d+\]/g, '')
    .replace(/[._]/g, ' ')
    .replace(/([a-záéíóúñ0-9])([A-ZÁÉÍÓÚÑ])/g, '$1 $2')
    .trim()
    .toLowerCase();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
