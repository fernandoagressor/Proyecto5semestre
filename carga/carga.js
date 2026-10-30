// Prueba de carga de NEXORA CORE (k6): usuarios reales por rol recorriendo la aplicación.
//
//   BASE_URL=https://staging.ejemplo.com CARGA_PASSWORD='...' VOLUMEN=completo \
//   ESCENARIO=carga k6 run carga/carga.js
//
// Necesita los usuarios carga.<rol>.<NNN> de carga/semilla.js (mismo VOLUMEN). Cada usuario
// virtual entra con uno de ellos (60 % asesores, 20 % servicio, 10 % cartera, 10 % gerencia) y
// repite pantallas de su rol con pausas de 2 a 6 s, como una persona.
//
// Escenarios (ESCENARIO): humo (5 usuarios, 1 min), carga (100 usuarios, 20 min), estres
// (escalones 100 → 150 → 200 → 300), pico (de 10 a 200 en 30 s), resistencia (100 usuarios, 60 min).
// Criterios: p95 de lecturas < 800 ms, de escrituras < 1,5 s, errores < 1 %.
// Resultado: resumen en pantalla y en resultados/ (JSON y Markdown).

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import {
  BASE, INSECURE, Sesion, azar, elegir, fechaIso, json, lista, pausa, requerido, usuariosDePrueba, volumen,
} from './lib/comun.js';

const ESCENARIO = __ENV.ESCENARIO || 'humo';
const ESCENARIOS = {
  humo: { executor: 'constant-vus', vus: 5, duration: __ENV.DURACION || '1m' },
  carga: {
    executor: 'ramping-vus', startVUs: 0,
    stages: [{ duration: '5m', target: 100 }, { duration: '20m', target: 100 }, { duration: '2m', target: 0 }],
  },
  estres: {
    executor: 'ramping-vus', startVUs: 0,
    stages: [
      { duration: '3m', target: 100 }, { duration: '5m', target: 100 },
      { duration: '2m', target: 150 }, { duration: '5m', target: 150 },
      { duration: '2m', target: 200 }, { duration: '5m', target: 200 },
      { duration: '2m', target: 300 }, { duration: '5m', target: 300 },
      { duration: '3m', target: 0 },
    ],
  },
  pico: {
    executor: 'ramping-vus', startVUs: 10,
    stages: [
      { duration: '2m', target: 10 }, { duration: '30s', target: 200 }, { duration: '3m', target: 200 },
      { duration: '30s', target: 10 }, { duration: '3m', target: 10 },
    ],
  },
  resistencia: {
    executor: 'ramping-vus', startVUs: 0,
    stages: [{ duration: '5m', target: 100 }, { duration: '60m', target: 100 }, { duration: '2m', target: 0 }],
  },
};
if (!ESCENARIOS[ESCENARIO]) throw new Error(`ESCENARIO debe ser uno de: ${Object.keys(ESCENARIOS).join(', ')}`);

// Nombres de las peticiones (sin llaves, comas ni dos puntos: k6 los usa para filtrar métricas).
const NOMBRES = [
  'POST /auth/login', 'GET /auth/me', 'GET /proyectos', 'GET /notificaciones/resumen',
  'GET /dashboards/comercial', 'GET /dashboards/servicio', 'GET /dashboards/cartera', 'GET /dashboards/gerencial',
  'GET /prospectos', 'GET /prospectos/ID', 'POST /prospectos',
  'GET /oportunidades', 'GET /oportunidades/ID', 'GET /oportunidades/ID/historial-etapas', 'PATCH /oportunidades/ID/etapa',
  'GET /proyectos/ID/inventario/resumen', 'GET /proyectos/ID/lotes', 'GET /proyectos/ID/lotes/ID', 'POST /ventas/cliente-lote',
  'GET /tareas', 'PATCH /tareas/ID/estado', 'GET /agenda/comercial', 'GET /simulaciones',
  'GET /solicitudes', 'GET /solicitudes/ID', 'GET /solicitudes/ID/mensajes', 'GET /solicitudes/ID/historial',
  'POST /solicitudes', 'POST /solicitudes/ID/mensajes', 'GET /citas-servicio',
  'GET /base-conocimiento/articulos', 'GET /base-conocimiento/categorias', 'GET /clientes', 'GET /clientes/ID',
  'GET /cartera/resumen', 'GET /cartera/mora', 'GET /cartera/gestiones-cobro', 'GET /contratos', 'GET /pagos',
  'GET /gerencia/rentabilidad', 'GET /metas-comerciales',
];

const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  'http_req_duration{tipo:lectura}': ['p(95)<800'],
  'http_req_duration{tipo:escritura}': ['p(95)<1500'],
  'http_req_duration{tipo:sesion}': ['p(95)<2000'],
};
// Umbrales que nunca fallan, solo para que el resumen traiga el detalle por petición.
for (const n of NOMBRES) {
  thresholds[`http_req_duration{name:${n}}`] = ['max>=0'];
  thresholds[`http_reqs{name:${n}}`] = ['count>=0'];
  thresholds[`http_req_failed{name:${n}}`] = ['rate>=0'];
}

export const options = {
  insecureSkipTLSVerify: INSECURE,
  scenarios: { [ESCENARIO]: ESCENARIOS[ESCENARIO] },
  thresholds,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const conflictos = new Counter('reservas_en_conflicto');
const etapasRechazadas = new Counter('cambios_de_etapa_rechazados');
// Reservar un lote que otro asesor acaba de tomar es un conflicto esperado, no un error.
const RESERVA_ESPERADA = http.expectedStatuses({ min: 200, max: 299 }, 400, 409, 422);

const V = volumen();
const USUARIOS = usuariosDePrueba(V);
const CLAVE = requerido('CARGA_PASSWORD');
const ETAPAS = ['PROSPECTO_RECIBIDO', 'CONTACTO_INICIAL', 'CALIFICACION', 'PRESENTACION_PROYECTO',
  'VISITA', 'PROPUESTA', 'NEGOCIACION', 'CIERRE'];

let yo = null; // { sesion, rol, id, proyectos }

function ok(r, que) {
  return check(r, { [que]: (x) => x.status >= 200 && x.status < 300 });
}

function iniciar() {
  const u = USUARIOS[(exec.vu.idInTest - 1) % USUARIOS.length];
  const sesion = new Sesion(u.usuario, CLAVE);
  const cuerpo = sesion.iniciar();
  const proyectos = lista(json(sesion.pedir('GET', '/api/v1/proyectos?tamano=50', null, 'GET /proyectos')))
    .map((p) => p.id);
  yo = { sesion, rol: u.rol, id: cuerpo.usuario.id, proyectos };
}

const pedir = (...args) => yo.sesion.pedir(...args);
const proyecto = () => elegir(yo.proyectos);

// Elige un recorrido según pesos: [[peso, función], ...]
function recorrer(opciones) {
  const total = opciones.reduce((a, [p]) => a + p, 0);
  let x = Math.random() * total;
  for (const [peso, f] of opciones) {
    if ((x -= peso) <= 0) return f();
  }
  return opciones[0][1]();
}

// ---------- Asesor comercial ----------
const asesor = [
  [25, () => {
    const texto = Math.random() < 0.3 ? `&texto=${elegir(['Ana', 'Luis', 'Prospecto', 'carga'])}` : '';
    const r = pedir('GET', `/api/v1/prospectos?tamano=20&pagina=${azar(0, 3)}${texto}`, null, 'GET /prospectos');
    ok(r, 'listar prospectos');
    const p = lista(json(r));
    if (p.length) {
      pausa();
      ok(pedir('GET', `/api/v1/prospectos/${elegir(p).id}`, null, 'GET /prospectos/ID'), 'ver prospecto');
    }
  }],
  [15, () => {
    const n = `${exec.vu.idInTest}-${exec.vu.iterationInInstance}-${Date.now() % 100000}`;
    ok(pedir('POST', '/api/v1/prospectos', {
      proyectoId: proyecto(), asesorId: yo.id, nombres: `Prospecto prueba ${n}`,
      telefono: `311${azar(1000000, 9999999)}`, correo: `p${n}@carga.nexora.test`,
      fuente: elegir(['WEB', 'REFERIDO', 'LLAMADA']), temperatura: elegir(['FRIO', 'TIBIO', 'CALIENTE']),
      presupuesto: azar(80, 400) * 1000000,
    }, 'POST /prospectos'), 'crear prospecto');
  }],
  [15, () => {
    const r = pedir('GET', `/api/v1/oportunidades?tamano=20&pagina=${azar(0, 2)}`, null, 'GET /oportunidades');
    ok(r, 'listar oportunidades');
    const o = lista(json(r));
    if (!o.length) return;
    const elegida = elegir(o);
    pausa();
    const d = pedir('GET', `/api/v1/oportunidades/${elegida.id}`, null, 'GET /oportunidades/ID');
    ok(d, 'ver oportunidad');
    ok(pedir('GET', `/api/v1/oportunidades/${elegida.id}/historial-etapas`, null, 'GET /oportunidades/ID/historial-etapas'),
      'historial de etapas');
    const actual = ETAPAS.indexOf((json(d) || {}).etapaActual);
    if (Math.random() < 0.3 && actual >= 0 && actual < ETAPAS.length - 2) {
      pausa();
      const c = pedir('PATCH', `/api/v1/oportunidades/${elegida.id}/etapa`,
        { etapa: ETAPAS[actual + 1], observacion: 'Avance en prueba de carga' },
        'PATCH /oportunidades/ID/etapa', 'escritura', { responseCallback: RESERVA_ESPERADA });
      if (c.status >= 400) etapasRechazadas.add(1);
      check(c, { 'cambio de etapa respondido': (x) => x.status < 500 });
    }
  }],
  [15, () => {
    const p = proyecto();
    ok(pedir('GET', `/api/v1/proyectos/${p}/inventario/resumen`, null, 'GET /proyectos/ID/inventario/resumen'), 'resumen de inventario');
    pausa();
    const r = pedir('GET', `/api/v1/proyectos/${p}/lotes?estado=DISPONIBLE&tamano=20&pagina=${azar(0, 2)}`, null,
      'GET /proyectos/ID/lotes');
    ok(r, 'listar lotes');
    const l = lista(json(r));
    if (l.length) {
      pausa();
      ok(pedir('GET', `/api/v1/proyectos/${p}/lotes/${elegir(l).id}`, null, 'GET /proyectos/ID/lotes/ID'), 'ver lote');
    }
  }],
  [5, () => {
    const p = proyecto();
    const r = pedir('GET', `/api/v1/proyectos/${p}/lotes?estado=DISPONIBLE&tamano=20`, null, 'GET /proyectos/ID/lotes');
    const l = lista(json(r));
    if (!l.length) return;
    pausa(3, 8);
    const n = `${exec.vu.idInTest}${exec.vu.iterationInInstance}${Date.now() % 100000}`;
    const f = pedir('POST', '/api/v1/ventas/cliente-lote', {
      cliente: {
        tipoPersona: 'NATURAL', tipoDocumento: 'CC', numeroDocumento: `6${n}`,
        nombres: 'Cliente', apellidos: `prueba ${n}`, telefono: `312${azar(1000000, 9999999)}`,
        correo: `c${n}@carga.nexora.test`, origen: 'CARGA',
      },
      proyectoId: p, loteId: elegir(l).id, fechaVencimiento: fechaIso(azar(5, 30)),
      valorReserva: 5000000, formaPago: 'TRANSFERENCIA',
    }, 'POST /ventas/cliente-lote', 'escritura', { responseCallback: RESERVA_ESPERADA });
    if (f.status >= 400 && f.status < 500) conflictos.add(1);
    check(f, { 'reserva respondida': (x) => x.status < 500 });
  }],
  [10, () => {
    ok(pedir('GET', '/api/v1/dashboards/comercial', null, 'GET /dashboards/comercial'), 'dashboard comercial');
    ok(pedir('GET', '/api/v1/notificaciones/resumen', null, 'GET /notificaciones/resumen'), 'notificaciones');
  }],
  [10, () => tareas()],
  [5, () => {
    ok(pedir('GET', `/api/v1/agenda/comercial?desde=${fechaIso(-7, 0)}&hasta=${fechaIso(14, 0)}`, null,
      'GET /agenda/comercial'), 'agenda');
    ok(pedir('GET', '/api/v1/simulaciones?tamano=20', null, 'GET /simulaciones'), 'simulaciones');
  }],
];

function tareas() {
  const r = pedir('GET', `/api/v1/tareas?asignadoA=${yo.id}&tamano=20`, null, 'GET /tareas');
  ok(r, 'listar tareas');
  const t = lista(json(r)).filter((x) => x.estado === 'PENDIENTE' || x.estado === 'EN_PROCESO');
  if (t.length && Math.random() < 0.5) {
    pausa();
    const tarea = elegir(t);
    ok(pedir('PATCH', `/api/v1/tareas/${tarea.id}/estado`, {
      estado: tarea.estado === 'PENDIENTE' ? 'EN_PROCESO' : 'COMPLETADA', comentario: 'Prueba de carga',
    }, 'PATCH /tareas/ID/estado'), 'cambiar estado de tarea');
  }
}

// ---------- Servicio al cliente ----------
const servicio = [
  [30, () => {
    const r = pedir('GET', `/api/v1/solicitudes?tamano=20&pagina=${azar(0, 3)}`, null, 'GET /solicitudes');
    ok(r, 'bandeja de PQRS');
    const s = lista(json(r));
    if (!s.length) return;
    const id = elegir(s).id;
    pausa();
    ok(pedir('GET', `/api/v1/solicitudes/${id}`, null, 'GET /solicitudes/ID'), 'ver PQRS');
    ok(pedir('GET', `/api/v1/solicitudes/${id}/mensajes`, null, 'GET /solicitudes/ID/mensajes'), 'mensajes de PQRS');
    ok(pedir('GET', `/api/v1/solicitudes/${id}/historial`, null, 'GET /solicitudes/ID/historial'), 'historial de PQRS');
    if (Math.random() < 0.5) {
      pausa(4, 10);
      ok(pedir('POST', `/api/v1/solicitudes/${id}/mensajes`, { mensaje: 'Respuesta de prueba de carga', interno: false },
        'POST /solicitudes/ID/mensajes'), 'responder PQRS');
    }
  }],
  [10, () => {
    ok(pedir('POST', '/api/v1/solicitudes', {
      proyectoId: proyecto(), tipo: elegir(['PETICION', 'QUEJA', 'RECLAMO']), categoria: 'Pagos',
      asunto: 'Solicitud registrada en prueba de carga', descripcion: 'Texto de prueba', prioridad: 'MEDIA',
    }, 'POST /solicitudes'), 'crear PQRS');
  }],
  [15, () => {
    ok(pedir('GET', '/api/v1/dashboards/servicio', null, 'GET /dashboards/servicio'), 'dashboard servicio');
    ok(pedir('GET', '/api/v1/notificaciones/resumen', null, 'GET /notificaciones/resumen'), 'notificaciones');
  }],
  [15, () => {
    ok(pedir('GET', '/api/v1/base-conocimiento/categorias', null, 'GET /base-conocimiento/categorias'), 'categorías');
    ok(pedir('GET', `/api/v1/base-conocimiento/articulos?texto=${elegir(['pago', 'escritura', 'lote'])}`, null,
      'GET /base-conocimiento/articulos'), 'buscar artículos');
  }],
  [10, () => ok(pedir('GET', `/api/v1/citas-servicio?desde=${fechaIso(-7, 0)}&hasta=${fechaIso(14, 0)}`, null,
    'GET /citas-servicio'), 'citas de servicio')],
  [10, () => clientes()],
  [10, () => tareas()],
];

function clientes() {
  const r = pedir('GET', `/api/v1/clientes?tamano=20&pagina=${azar(0, 5)}`, null, 'GET /clientes');
  ok(r, 'listar clientes');
  const c = lista(json(r));
  if (c.length) {
    pausa();
    ok(pedir('GET', `/api/v1/clientes/${elegir(c).id}`, null, 'GET /clientes/ID'), 'ver cliente');
  }
}

// ---------- Cartera ----------
const cartera = [
  [30, () => ok(pedir('GET', '/api/v1/dashboards/cartera', null, 'GET /dashboards/cartera'), 'dashboard cartera')],
  [20, () => {
    ok(pedir('GET', '/api/v1/cartera/resumen', null, 'GET /cartera/resumen'), 'resumen de cartera');
    ok(pedir('GET', '/api/v1/cartera/mora', null, 'GET /cartera/mora'), 'mora');
  }],
  [15, () => ok(pedir('GET', '/api/v1/contratos?tamano=20', null, 'GET /contratos'), 'contratos')],
  [15, () => ok(pedir('GET', '/api/v1/pagos?tamano=20', null, 'GET /pagos'), 'pagos')],
  [10, () => ok(pedir('GET', '/api/v1/cartera/gestiones-cobro', null, 'GET /cartera/gestiones-cobro'), 'gestiones de cobro')],
  [10, () => clientes()],
];

// ---------- Gerencia ----------
const gerencia = [
  [40, () => ok(pedir('GET', '/api/v1/dashboards/gerencial', null, 'GET /dashboards/gerencial'), 'dashboard gerencial')],
  [15, () => ok(pedir('GET', '/api/v1/gerencia/rentabilidad', null, 'GET /gerencia/rentabilidad'), 'rentabilidad')],
  [15, () => ok(pedir('GET', `/api/v1/oportunidades?proyectoId=${proyecto()}&tamano=20`, null, 'GET /oportunidades'),
    'oportunidades del proyecto')],
  [15, () => ok(pedir('GET', `/api/v1/proyectos/${proyecto()}/inventario/resumen`, null,
    'GET /proyectos/ID/inventario/resumen'), 'inventario del proyecto')],
  [15, () => ok(pedir('GET', '/api/v1/metas-comerciales', null, 'GET /metas-comerciales'), 'metas')],
];

const RECORRIDOS = { asesor, servicio, cartera, gerencia };

export default function () {
  if (!yo) iniciar();
  recorrer(RECORRIDOS[yo.rol]);
  pausa(2, 6);
}

// ---------- Resumen ----------
function fila(datos, nombre) {
  const d = datos.metrics[`http_req_duration{name:${nombre}}`];
  const n = datos.metrics[`http_reqs{name:${nombre}}`];
  const f = datos.metrics[`http_req_failed{name:${nombre}}`];
  if (!d || !n || !n.values.count) return null;
  return { nombre, n: n.values.count, med: d.values.med, p95: d.values['p(95)'], max: d.values.max, err: f ? f.values.rate : 0 };
}

export function handleSummary(datos) {
  const ms = (x) => (x === undefined ? '–' : `${Math.round(x)} ms`);
  const m = datos.metrics;
  const criterios = Object.entries(m)
    .filter(([nombre, v]) => v.thresholds && !nombre.includes('{name:'))
    .flatMap(([nombre, v]) => Object.entries(v.thresholds).map(([t, r]) => `| ${r.ok ? '✅' : '❌'} | \`${nombre}\` | \`${t}\` |`));
  const filas = NOMBRES.map((n) => fila(datos, n)).filter(Boolean).sort((a, b) => b.p95 - a.p95);
  const duracion = datos.state.testRunDurationMs / 1000;
  const md = [
    `## Prueba de carga: ${ESCENARIO}`,
    '',
    `Contra \`${BASE}\` · ${Math.round(duracion / 60)} min · hasta ${m.vus_max ? m.vus_max.values.max : '?'} usuarios · `
      + `${m.http_reqs.values.count} peticiones (${m.http_reqs.values.rate.toFixed(1)}/s) · `
      + `errores ${(m.http_req_failed.values.rate * 100).toFixed(2)} %`,
    '',
    '| | Métrica | Criterio |', '|---|---|---|', ...criterios,
    '',
    `Reservas en conflicto (esperadas): ${m.reservas_en_conflicto ? m.reservas_en_conflicto.values.count : 0} · `
      + `cambios de etapa rechazados: ${m.cambios_de_etapa_rechazados ? m.cambios_de_etapa_rechazados.values.count : 0}`,
    '',
    '| Petición | Cantidad | Mediana | p95 | Máximo | Errores |', '|---|---:|---:|---:|---:|---:|',
    ...filas.map((f) => `| ${f.nombre} | ${f.n} | ${ms(f.med)} | ${ms(f.p95)} | ${ms(f.max)} | ${(f.err * 100).toFixed(1)} % |`),
    '',
  ].join('\n');
  const sello = new Date().toISOString().slice(0, 16).replace(/[-:T]/g, '');
  return {
    stdout: textSummary(datos, { indent: ' ', enableColors: false }),
    [`resultados/${ESCENARIO}-${sello}.json`]: JSON.stringify(datos, null, 1),
    [`resultados/${ESCENARIO}-${sello}.md`]: md,
    'resultados/ultimo.md': md,
  };
}
