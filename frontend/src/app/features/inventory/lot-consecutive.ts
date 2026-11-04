import { Lot } from '../../core/models/inventory.models';

export interface LotConsecutive {
  numero: string;
  codigo: string;
}

/**
 * Siguiente número y código para un lote nuevo dentro de una manzana.
 *
 * El número es el mayor número entero de la manzana más uno. El código sigue el patrón del último
 * lote de la manzana (se reemplaza su número final respetando los ceros a la izquierda); si la
 * manzana aún no tiene lotes, se arma con los códigos de etapa y manzana: `E02-M03-L01`.
 * El backend sigue validando que código y número no se repitan.
 */
export function nextLotConsecutive(
  blockLots: readonly Pick<Lot, 'numero' | 'codigo'>[],
  stageCode: string,
  blockCode: string,
): LotConsecutive {
  const numbers = blockLots
    .map((lot) => Number.parseInt(lot.numero, 10))
    .filter((value) => Number.isFinite(value));
  const next = (numbers.length ? Math.max(...numbers) : 0) + 1;

  const last = [...blockLots]
    .filter((lot) => Number.isFinite(Number.parseInt(lot.numero, 10)))
    .sort((a, b) => Number.parseInt(b.numero, 10) - Number.parseInt(a.numero, 10))[0];
  const trailing = last?.codigo.match(/^(.*?)(\d+)$/);
  if (trailing) {
    const [, prefix, digits] = trailing;
    return { numero: `${next}`, codigo: `${prefix}${`${next}`.padStart(digits.length, '0')}` };
  }

  const parts = [stageCode, blockCode].map((code) => code.trim().toUpperCase()).filter(Boolean);
  return { numero: `${next}`, codigo: [...parts, `L${`${next}`.padStart(2, '0')}`].join('-') };
}
