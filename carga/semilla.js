// Genera datos de volumen en staging por la API (respeta reglas de negocio, MongoDB, espejos y
// auditoría), para que las pruebas de carga corran sobre una base parecida a la real.
//
//   BASE_URL=https://staging.ejemplo.com ADMIN_USUARIO=SUPERADMIN ADMIN_PASSWORD='...' \
//   CARGA_PASSWORD='...' VOLUMEN=completo k6 run carga/semilla.js
//
// Crea (VOLUMEN=completo): 10 proyectos con 2 etapas, 5 manzanas y 2.000 lotes (20 % reservados
// con cliente, prospecto, oportunidad y reserva), 100 usuarios de prueba carga.<rol>.<NNN>
// (60 asesores, 20 de servicio, 10 de cartera, 10 de gerencia), 2.000 clientes, 5.000 prospectos
// (la mitad con oportunidad), 4.000 tareas y 1.500 PQRS. Sube los límites de la empresa si hace falta.
// Se puede volver a correr: los usuarios se reutilizan y el resto se suma con otro sello.
// NUNCA en producción: crea usuarios con una contraseña conocida por el equipo de pruebas.

import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE, INSECURE, ROLES, Sesion, azar, elegir, fechaIso, json, lista, requerido, usuariosDePrueba, volumen,
} from './lib/comun.js';

const V = volumen();
const SELLO = __ENV.SEMILLA || String(Math.floor(Date.now() / 1000) % 100000000);
const LOTES = V.proyectos * V.etapas * V.manzanas * V.lotesPorManzana;
const TAREAS = [
  ['lote', LOTES], ['cliente', V.clientes], ['prospecto', V.prospectos], ['tarea', V.tareas], ['pqrs', V.pqrs],
];
const TOTAL = TAREAS.reduce((a, [, n]) => a + n, 0);
const creados = new Counter('semilla_creados');

export const options = {
  setupTimeout: '30m',
  insecureSkipTLSVerify: INSECURE,
  scenarios: {
    datos: {
      executor: 'shared-iterations', vus: Number(__ENV.VUS || 10), iterations: TOTAL, maxDuration: '90m',
    },
  },
  thresholds: { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] },
};

function admin() {
  const s = new Sesion(requerido('ADMIN_USUARIO'), requerido('ADMIN_PASSWORD'));
  const r = s.iniciar();
  if (r.usuario && r.usuario.requiereCambioPassword) {
    fail('El administrador todavía tiene la contraseña temporal: cámbiela en la aplicación primero');
  }
  return s;
}

function exigir(r, esperado, que) {
  if (r.status < 200 || r.status > 299) fail(`${que}: HTTP ${r.status} ${String(r.body).slice(0, 300)}`);
  return json(r); // algunas respuestas (cambiar contraseña) no tienen cuerpo
}

export function setup() {
  const clave = requerido('CARGA_PASSWORD');
  const s = admin();
  console.log(`Semilla ${SELLO} (${__ENV.VOLUMEN || 'pequeno'}) contra ${BASE}: ${TOTAL} registros`);

  // 1. Límites de la empresa (por defecto 1 proyecto y 5 usuarios).
  const empresaId = exigir(s.pedir('GET', '/api/v1/empresas/actual', null, 'GET /empresas/actual'), 200, 'Empresa actual').id;
  const e = exigir(s.pedir('GET', `/api/v1/admin/empresas/${empresaId}`, null, 'GET /admin/empresas/{id}'), 200, 'Empresa');
  const pruebas = usuariosDePrueba(V);
  const limiteProyectos = Math.max(e.limiteProyectos, e.totalProyectos + V.proyectos);
  const limiteUsuarios = Math.max(e.limiteUsuarios, e.totalUsuarios + pruebas.length);
  if (limiteProyectos !== e.limiteProyectos || limiteUsuarios !== e.limiteUsuarios) {
    exigir(s.pedir('PUT', `/api/v1/admin/empresas/${empresaId}`, {
      codigo: e.codigo, nombre: e.nombre, nit: e.nit, direccion: e.direccion, ciudad: e.ciudad,
      departamento: e.departamento, telefono: e.telefono, correo: e.correo, logoUrl: e.logoUrl,
      zonaHoraria: e.zonaHoraria, moneda: e.moneda, limiteProyectos, limiteUsuarios,
      modulos: e.modulos, version: e.version,
    }, 'PUT /admin/empresas/{id}'), 200, 'Subir límites de la empresa');
    console.log(`Límites de la empresa: ${limiteProyectos} proyectos, ${limiteUsuarios} usuarios`);
  }

  // 2. Proyectos con etapas y manzanas, activos para vender.
  const proyectos = [];
  for (let p = 1; p <= V.proyectos; p++) {
    const proyecto = exigir(s.pedir('POST', '/api/v1/proyectos', {
      codigo: `CARGA${SELLO}-${p}`, nombre: `Proyecto de carga ${SELLO}-${p}`,
      ciudad: elegir(['Cartagena', 'Barranquilla', 'Santa Marta']), departamento: 'Bolívar',
      maxLotes: V.etapas * V.manzanas * V.lotesPorManzana,
    }, 'POST /proyectos'), 201, 'Crear proyecto');
    const etapas = [];
    for (let et = 1; et <= V.etapas; et++) {
      const etapa = exigir(s.pedir('POST', `/api/v1/proyectos/${proyecto.id}/etapas`,
        { codigo: `E${et}`, nombre: `Etapa ${et}`, secuencia: et, estado: 'ACTIVA' },
        'POST /proyectos/{id}/etapas'), 201, 'Crear etapa');
      const manzanas = [];
      for (let m = 1; m <= V.manzanas; m++) {
        manzanas.push(exigir(s.pedir('POST', `/api/v1/proyectos/${proyecto.id}/etapas/${etapa.id}/manzanas`,
          { codigo: `M${m}`, nombre: `Manzana ${m}`, secuencia: m, estado: 'ACTIVA' },
          'POST /proyectos/{id}/etapas/{id}/manzanas'), 201, 'Crear manzana').id);
      }
      etapas.push({ id: etapa.id, manzanas });
    }
    exigir(s.pedir('PATCH', `/api/v1/proyectos/${proyecto.id}/estado`, { estado: 'ACTIVO' },
      'PATCH /proyectos/{id}/estado'), 200, 'Activar proyecto');
    proyectos.push({ id: proyecto.id, etapas });
  }

  // 3. Usuarios de prueba (se reutilizan si ya existen).
  const roles = Object.fromEntries(exigir(s.pedir('GET', `/api/v1/admin/empresas/${empresaId}/roles`, null,
    'GET /admin/empresas/{id}/roles'), 200, 'Roles').map((r) => [r.codigo, r.id]));
  const existentes = Object.fromEntries(lista(exigir(s.pedir('GET',
    `/api/v1/admin/empresas/${empresaId}/usuarios`, null, 'GET /admin/empresas/{id}/usuarios'),
  200, 'Usuarios')).map((u) => [u.nombreUsuario.toLowerCase(), u.id]));
  const temporal = `Tmp-${clave}-9z`;
  const usuarios = [];
  let nuevos = 0;
  pruebas.forEach((u, i) => {
    let id = existentes[u.usuario];
    if (!id) {
      const n = Number(u.usuario.slice(-3));
      const alcances = u.rol === 'asesor'
        ? [proyectos[n % proyectos.length], proyectos[(n + 1) % proyectos.length]]
          .map((p) => ({ tipo: 'PROYECTO', proyectoId: p.id }))
        : [{ tipo: 'EMPRESA' }];
      id = exigir(s.pedir('POST', `/api/v1/admin/empresas/${empresaId}/usuarios`, {
        tipoDocumento: 'CC', numeroDocumento: `88${Object.keys(ROLES).indexOf(u.rol)}${String(i).padStart(5, '0')}`,
        nombres: `Prueba ${u.rol}`, apellidos: u.usuario.slice(-3), nombreUsuario: u.usuario,
        correo: `${u.usuario}@carga.nexora.test`, passwordTemporal: temporal,
        rolIds: [roles[ROLES[u.rol]]], alcances,
      }, 'POST /admin/empresas/{id}/usuarios'), 201, `Crear ${u.usuario}`).id;
      const propia = new Sesion(u.usuario, temporal);
      propia.iniciar();
      exigir(propia.pedir('POST', '/api/v1/auth/cambiar-password',
        { passwordActual: temporal, passwordNueva: clave, confirmacionPassword: clave },
        'POST /auth/cambiar-password'), 200, `Contraseña de ${u.usuario}`);
      nuevos++;
    }
    usuarios.push({ id, rol: u.rol, usuario: u.usuario });
  });
  console.log(`Usuarios de prueba: ${usuarios.length} (${nuevos} nuevos). Proyectos: ${proyectos.map((p) => p.id)}`);

  // 4. Clientes a los que se asocian las PQRS (toda PQRS debe indicar el cliente).
  const clientesPqrs = [];
  for (let c = 0; c < Math.min(30, V.pqrs); c++) {
    clientesPqrs.push(exigir(s.pedir('POST', '/api/v1/clientes', cliente(3, c), 'POST /clientes'), 201, 'Cliente de PQRS').id);
  }
  return { proyectos, usuarios, clientesPqrs };
}

let sesion = null;

function lote(datos, i) {
  const porProyecto = V.etapas * V.manzanas * V.lotesPorManzana;
  const p = datos.proyectos[Math.floor(i / porProyecto)];
  const resto = i % porProyecto;
  const etapa = p.etapas[Math.floor(resto / (V.manzanas * V.lotesPorManzana))];
  const manzana = etapa.manzanas[Math.floor((resto % (V.manzanas * V.lotesPorManzana)) / V.lotesPorManzana)];
  const numero = (resto % V.lotesPorManzana) + 1;
  const area = azar(120, 600);
  const r = sesion.pedir('POST', `/api/v1/proyectos/${p.id}/lotes`, {
    etapaId: etapa.id, manzanaId: manzana, codigo: `L${SELLO}-${i}`, numero: String(numero),
    areaM2: area, frenteM: 10, fondoM: Math.round(area / 10), valorLista: area * azar(180000, 420000),
  }, 'POST /proyectos/{id}/lotes');
  if (!check(r, { 'lote creado': (x) => x.status >= 200 && x.status < 300 })) return console.warn(`Lote: ${r.status} ${r.body}`);
  creados.add(1, { tipo: 'lote' });
  if (Math.random() < V.reservadosPorc) {
    const f = sesion.pedir('POST', '/api/v1/ventas/cliente-lote', {
      cliente: cliente(2, i), proyectoId: p.id, loteId: r.json().id,
      fechaVencimiento: fechaIso(azar(5, 30)), valorReserva: 5000000, formaPago: 'TRANSFERENCIA',
    }, 'POST /ventas/cliente-lote');
    if (check(f, { 'reserva creada': (x) => x.status >= 200 && x.status < 300 })) creados.add(1, { tipo: 'reserva' });
    else console.warn(`Reserva: ${f.status} ${f.body}`);
  }
}

// prefijo 1: clientes sueltos; 2: con reserva; 3: de las PQRS (números de documento distintos).
function cliente(prefijo, i) {
  const sufijo = `${prefijo}${String(i).padStart(6, '0')}`;
  return {
    tipoPersona: 'NATURAL', tipoDocumento: 'CC', numeroDocumento: `7${SELLO}${sufijo}`,
    nombres: elegir(['Ana', 'Luis', 'María', 'Carlos', 'Paola', 'Jorge', 'Diana', 'Andrés']),
    apellidos: `${elegir(['Pérez', 'Gómez', 'Díaz', 'Rojas', 'Torres'])} carga`,
    telefono: `300${azar(1000000, 9999999)}`, correo: `cliente${sufijo}.${SELLO}@carga.nexora.test`,
    origen: 'CARGA',
  };
}

export default function (datos) {
  if (!sesion) sesion = admin();
  let i = exec.scenario.iterationInTest;
  let tipo = null;
  for (const [t, n] of TAREAS) {
    if (i < n) { tipo = t; break; }
    i -= n;
  }
  const proyecto = elegir(datos.proyectos);
  const asesores = datos.usuarios.filter((u) => u.rol === 'asesor');

  if (tipo === 'lote') {
    lote(datos, i);
  } else if (tipo === 'cliente') {
    const r = sesion.pedir('POST', '/api/v1/clientes', cliente(1, i), 'POST /clientes');
    if (check(r, { 'cliente creado': (x) => x.status >= 200 && x.status < 300 })) creados.add(1, { tipo: 'cliente' });
    else console.warn(`Cliente: ${r.status} ${r.body}`);
  } else if (tipo === 'prospecto') {
    const asesor = asesores.length ? elegir(asesores).id : null;
    const r = sesion.pedir('POST', '/api/v1/prospectos', {
      proyectoId: proyecto.id, asesorId: asesor, nombres: `Prospecto ${SELLO}-${i}`,
      telefono: `310${azar(1000000, 9999999)}`, correo: `prospecto${i}.${SELLO}@carga.nexora.test`,
      fuente: elegir(['WEB', 'REDES_SOCIALES', 'REFERIDO', 'PUBLICIDAD', 'FERIA', 'LLAMADA', 'VISITA']),
      temperatura: elegir(['FRIO', 'TIBIO', 'CALIENTE']), presupuesto: azar(80, 400) * 1000000,
    }, 'POST /prospectos');
    if (!check(r, { 'prospecto creado': (x) => x.status >= 200 && x.status < 300 })) return console.warn(`Prospecto: ${r.status} ${r.body}`);
    creados.add(1, { tipo: 'prospecto' });
    if (Math.random() < V.oportunidadesPorc) {
      // Solo un prospecto calificado puede tener oportunidad.
      const temperatura = elegir(['TIBIO', 'CALIENTE']);
      const c = sesion.pedir('PATCH', `/api/v1/prospectos/${r.json().id}/calificacion`,
        { estado: 'CALIFICADO', temperatura, justificacion: 'Calificado por la semilla de carga' },
        'PATCH /prospectos/{id}/calificacion');
      if (!check(c, { 'prospecto calificado': (x) => x.status >= 200 && x.status < 300 })) {
        return console.warn(`Calificar: ${c.status} ${c.body}`);
      }
      const o = sesion.pedir('POST', '/api/v1/oportunidades', {
        proyectoId: proyecto.id, prospectoId: r.json().id, asesorId: asesor,
        valorEstimado: azar(80, 400) * 1000000, fechaProximaAccion: fechaIso(azar(1, 20)),
      }, 'POST /oportunidades');
      if (check(o, { 'oportunidad creada': (x) => x.status >= 200 && x.status < 300 })) creados.add(1, { tipo: 'oportunidad' });
      else console.warn(`Oportunidad: ${o.status} ${o.body}`);
    }
  } else if (tipo === 'tarea') {
    const r = sesion.pedir('POST', '/api/v1/tareas', {
      proyectoId: proyecto.id, asignadoA: elegir(datos.usuarios).id,
      titulo: elegir(['Llamar al cliente', 'Enviar propuesta', 'Revisar documentos', 'Agendar visita']),
      tipo: 'seguimiento', prioridad: elegir(['BAJA', 'MEDIA', 'ALTA']), fechaVencimiento: fechaIso(azar(-5, 30)),
    }, 'POST /tareas');
    if (check(r, { 'tarea creada': (x) => x.status >= 200 && x.status < 300 })) creados.add(1, { tipo: 'tarea' });
    else console.warn(`Tarea: ${r.status} ${r.body}`);
  } else if (tipo === 'pqrs') {
    const r = sesion.pedir('POST', '/api/v1/solicitudes', {
      proyectoId: proyecto.id, clienteId: elegir(datos.clientesPqrs),
      tipo: elegir(['PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA', 'SOLICITUD']),
      categoria: elegir(['Pagos', 'Escrituración', 'Obras', 'Documentos']),
      asunto: `Solicitud de carga ${SELLO}-${i}`, descripcion: 'Generada por carga/semilla.js',
      prioridad: elegir(['BAJA', 'MEDIA', 'ALTA']),
    }, 'POST /solicitudes');
    if (check(r, { 'PQRS creada': (x) => x.status >= 200 && x.status < 300 })) creados.add(1, { tipo: 'pqrs' });
    else console.warn(`PQRS: ${r.status} ${r.body}`);
  }
}

export function teardown() {
  console.log(`Semilla ${SELLO} terminada contra ${BASE}`);
}
