// Piezas compartidas por carga/semilla.js y carga/carga.js (k6).
import http from 'k6/http';
import { sleep } from 'k6';

export const BASE = (__ENV.BASE_URL || 'http://localhost:4200').replace(/\/$/, '');
export const INSECURE = __ENV.INSECURE === '1';
const JSON_H = { 'Content-Type': 'application/json', Accept: 'application/json' };

// Volúmenes de datos. "pequeno" valida los scripts en el CI; "completo" es el de staging.
// Usuarios de prueba: carga.<rol>.<NNN>, por ejemplo carga.asesor.001.
export const VOLUMENES = {
  pequeno: {
    proyectos: 2, etapas: 1, manzanas: 2, lotesPorManzana: 6, reservadosPorc: 0.2,
    usuarios: { asesor: 4, servicio: 2, cartera: 1, gerencia: 1 },
    clientes: 20, prospectos: 40, oportunidadesPorc: 0.5, tareas: 30, pqrs: 20,
  },
  completo: {
    proyectos: 10, etapas: 2, manzanas: 5, lotesPorManzana: 20, reservadosPorc: 0.2,
    usuarios: { asesor: 60, servicio: 20, cartera: 10, gerencia: 10 },
    clientes: 2000, prospectos: 5000, oportunidadesPorc: 0.5, tareas: 4000, pqrs: 1500,
  },
};
export const ROLES = {
  asesor: 'ASESOR_COMERCIAL',
  servicio: 'SERVICIO_CLIENTE',
  cartera: 'CARTERA',
  gerencia: 'GERENCIA',
};

export function volumen() {
  const v = VOLUMENES[__ENV.VOLUMEN || 'pequeno'];
  if (!v) throw new Error(`VOLUMEN debe ser ${Object.keys(VOLUMENES).join(' o ')}`);
  return v;
}

export const nombreUsuario = (rol, n) => `carga.${rol}.${String(n).padStart(3, '0')}`;

// Todos los usuarios de prueba, intercalados por rol en proporción a su cantidad: así cualquier
// número de usuarios virtuales reparte bien los roles (60 % asesores, 20 % servicio...).
export function usuariosDePrueba(v) {
  const total = Object.values(v.usuarios).reduce((a, b) => a + b, 0);
  const lista = [];
  const usados = Object.fromEntries(Object.keys(v.usuarios).map((r) => [r, 0]));
  for (let i = 0; i < total; i++) {
    let elegido = null;
    let mayorDeficit = -Infinity;
    for (const [rol, cantidad] of Object.entries(v.usuarios)) {
      if (usados[rol] >= cantidad) continue;
      const deficit = ((i + 1) * cantidad) / total - usados[rol];
      if (deficit > mayorDeficit) { mayorDeficit = deficit; elegido = rol; }
    }
    usados[elegido] += 1;
    lista.push({ rol: elegido, usuario: nombreUsuario(elegido, usados[elegido]) });
  }
  return lista;
}

export function requerido(nombre) {
  const valor = __ENV[nombre];
  if (!valor) throw new Error(`Falta la variable ${nombre}`);
  return valor;
}

export const azar = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
export const elegir = (lista) => lista[Math.floor(Math.random() * lista.length)];
export const pausa = (min = 2, max = 6) => sleep(min + Math.random() * (max - min));
export const lista = (cuerpo) => (Array.isArray(cuerpo) ? cuerpo : (cuerpo && cuerpo.contenido) || []);

export function fechaIso(diasDesdeHoy, hora = 10) {
  const d = new Date(Date.now() + diasDesdeHoy * 86400000);
  d.setUTCHours(hora, 0, 0, 0);
  return d.toISOString().slice(0, 19);
}

export function json(r) {
  try { return r.json(); } catch (e) { return null; }
}

// Sesión de un usuario: inicia sesión, renueva el token antes de que venza (15 min) y reintenta
// una vez si la API responde 401.
export class Sesion {
  constructor(usuario, clave) {
    this.usuario = usuario;
    this.clave = clave;
    this.token = null;
    this.desde = 0;
    this.datos = null;
  }

  iniciar() {
    const r = http.post(`${BASE}/api/v1/auth/login`,
      JSON.stringify({ identificador: this.usuario, password: this.clave }),
      { headers: JSON_H, tags: { name: 'POST /auth/login', tipo: 'sesion' } });
    if (r.status !== 200) {
      throw new Error(`No se pudo iniciar sesión como ${this.usuario}: HTTP ${r.status} ${r.body && r.body.slice(0, 200)}`);
    }
    const cuerpo = r.json();
    this.token = cuerpo.accessToken;
    this.datos = cuerpo.usuario;
    this.desde = Date.now();
    return cuerpo;
  }

  // pedir('GET', '/api/v1/prospectos/1', null, 'GET /prospectos/{id}', 'lectura')
  pedir(metodo, ruta, cuerpo, nombre, tipo, extra = {}) {
    if (!this.token || Date.now() - this.desde > 12 * 60 * 1000) this.iniciar();
    const enviar = () => http.request(metodo, `${BASE}${ruta}`,
      cuerpo === null || cuerpo === undefined ? null : JSON.stringify(cuerpo),
      Object.assign({
        headers: Object.assign({ Authorization: `Bearer ${this.token}` }, JSON_H),
        tags: { name: nombre || `${metodo} ${ruta}`, tipo: tipo || (metodo === 'GET' ? 'lectura' : 'escritura') },
      }, extra));
    let r = enviar();
    if (r.status === 401) {
      this.iniciar();
      r = enviar();
    }
    return r;
  }
}
