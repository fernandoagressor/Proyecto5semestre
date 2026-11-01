/** Contraseña temporal de 20 caracteres que cumple la política del backend (mayúscula, minúscula, número y símbolo). */
export function generateTemporaryPassword(length = 20): string {
  const uppercase = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const lowercase = 'abcdefghijkmnopqrstuvwxyz';
  const digits = '23456789';
  const symbols = '!@#$%&*?';
  const alphabet = uppercase + lowercase + digits + symbols;
  const random = new Uint32Array(length);
  globalThis.crypto.getRandomValues(random);
  const characters = [
    uppercase[random[0] % uppercase.length],
    lowercase[random[1] % lowercase.length],
    digits[random[2] % digits.length],
    symbols[random[3] % symbols.length],
  ];
  for (let i = 4; i < length; i += 1) characters.push(alphabet[random[i] % alphabet.length]);
  for (let i = characters.length - 1; i > 0; i -= 1) {
    const j = random[i] % (i + 1);
    [characters[i], characters[j]] = [characters[j], characters[i]];
  }
  return characters.join('');
}

export const STRONG_TEMPORARY_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])\S{12,72}$/;
