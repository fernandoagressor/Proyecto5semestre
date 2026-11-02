// Capturas de pantalla de la aplicación para revisar el diseño en cada PR.
//
//   BASE_URL=http://localhost:4200 USUARIO=SUPERADMIN PASSWORD='...' node scripts/capturas.mjs
//
// Inicia sesión por la pantalla de login (como un usuario), recorre las rutas principales en
// escritorio (1440 px) y en celular (390 px) y guarda los PNG en ./capturas, junto con
// errores.txt: los errores de la consola del navegador y las respuestas 5xx de la API.
// Lo corre .github/workflows/humo-e2e.yml y sube la carpeta como artefacto "capturas".
import { mkdirSync, writeFileSync } from 'node:fs';
import { chromium } from 'playwright';

const BASE = (process.env.BASE_URL || 'http://localhost:4200').replace(/\/$/, '');
const USUARIO = process.env.USUARIO;
const PASSWORD = process.env.PASSWORD;
if (!USUARIO || !PASSWORD) throw new Error('Faltan USUARIO y PASSWORD');

const RUTAS = [
  'dashboard',
  'superadmin',
  'proyectos',
  'inventario',
  'crm',
  'clientes/nuevo',
  'simulador',
  'ventas',
  'cartera',
  'servicio',
  'gerencia',
  'auditoria',
  'usuarios',
  'seguridad/cambiar-password',
];
const MOVIL = ['dashboard', 'inventario', 'cartera'];
const SALIDA = 'capturas';
// Con CAPTURAS_JPG=1 guarda además una versión liviana (JPEG, hasta 2.600 px de alto) en
// capturas/jpg para imprimirla en el log del CI: así se revisa el diseño sin descargar artefactos.
const JPG = process.env.CAPTURAS_JPG === '1';
mkdirSync(`${SALIDA}/jpg`, { recursive: true });

async function capturar(page, archivo) {
  await page.screenshot({ path: `${SALIDA}/${archivo}.png`, fullPage: true });
  if (!JPG) return;
  const alto = await page.evaluate(() => document.documentElement.scrollHeight);
  const ancho = page.viewportSize().width;
  await page.screenshot({
    path: `${SALIDA}/jpg/${archivo}.jpg`, type: 'jpeg', quality: 45, fullPage: true,
    clip: { x: 0, y: 0, width: ancho, height: Math.min(alto, 2600) },
  });
}

const errores = [];
const nombre = (ruta) => ruta.replace(/\//g, '-');

async function vigilar(page, vista) {
  page.on('console', (m) => m.type() === 'error' && errores.push(`[${vista}] consola: ${m.text()}`));
  page.on('pageerror', (e) => errores.push(`[${vista}] excepción: ${e.message}`));
  page.on('response', (r) => r.status() >= 500 && errores.push(`[${vista}] HTTP ${r.status()} ${r.url()}`));
}

async function esperar(page) {
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function iniciarSesion(page, vista, usuario = USUARIO, password = PASSWORD) {
  await page.goto(`${BASE}/login`);
  await esperar(page);
  if (usuario === USUARIO) await capturar(page, `${vista}-00-login`);
  await page.locator('[formcontrolname="identificador"]').fill(usuario);
  await page.locator('[formcontrolname="password"]').fill(password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 });
  await esperar(page);
}

async function recorrer(browser, vista, viewport, rutas) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 1, locale: 'es-CO', timezoneId: 'America/Bogota' });
  const page = await context.newPage();
  await vigilar(page, vista);
  await iniciarSesion(page, vista);
  let i = 1;
  for (const ruta of rutas) {
    await page.goto(`${BASE}/${ruta}`);
    await esperar(page);
    const archivo = `${vista}-${String(i++).padStart(2, '0')}-${nombre(ruta)}`;
    await capturar(page, archivo);
    console.log(`${archivo}  (${page.url().replace(BASE, '')})`);
  }
  if (vista === 'movil') {
    const menu = page.getByRole('button', { name: 'Abrir menú' });
    if (await menu.isVisible()) {
      await menu.click();
      await page.waitForTimeout(400);
      await capturar(page, 'movil-menu');
    }
  }
  await context.close();
}

// Portal del cliente: habilita el acceso de un cliente (de preferencia uno con contrato), cambia
// su contraseña temporal por la API y captura el portal como lo ve él, en escritorio y celular.
async function api(metodo, ruta, token, cuerpo) {
  const r = await fetch(`${BASE}/api/v1${ruta}`, {
    method: metodo,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: cuerpo ? JSON.stringify(cuerpo) : undefined,
  });
  const texto = await r.text();
  return { status: r.status, json: texto ? JSON.parse(texto) : null };
}

async function prepararCliente() {
  const admin = (await api('POST', '/auth/login', null, { identificador: USUARIO, password: PASSWORD })).json?.accessToken;
  if (!admin) return null;
  const contratos = (await api('GET', '/contratos?tamano=1', admin)).json?.contenido ?? [];
  const clientes = (await api('GET', '/clientes?tamano=20', admin)).json?.contenido ?? [];
  const id = contratos[0]?.clientePrincipalId ?? clientes.find((c) => c.correo && !c.usuarioId)?.id ?? clientes[0]?.id;
  if (!id) return null;
  let acceso = await api('POST', `/clientes/${id}/portal`, admin);
  if (acceso.status === 409) acceso = await api('POST', `/clientes/${id}/portal/reenviar`, admin);
  if (!acceso.json?.passwordTemporal) {
    errores.push(`[portal] no se pudo habilitar el cliente ${id}: HTTP ${acceso.status}`);
    return null;
  }
  const { nombreUsuario, passwordTemporal } = acceso.json;
  const nueva = `Captura-${Date.now()}-Ok9!`;
  const token = (await api('POST', '/auth/login', null, { identificador: nombreUsuario, password: passwordTemporal })).json?.accessToken;
  const cambio = await api('POST', '/auth/cambiar-password', token, {
    passwordActual: passwordTemporal, passwordNueva: nueva, confirmacionPassword: nueva,
  });
  return cambio.status < 300 ? { usuario: nombreUsuario, password: nueva } : null;
}

async function portal(browser, cliente) {
  for (const [vista, viewport] of [['escritorio', { width: 1440, height: 900 }], ['movil', { width: 390, height: 844 }]]) {
    const context = await browser.newContext({ viewport, deviceScaleFactor: 1, locale: 'es-CO', timezoneId: 'America/Bogota' });
    const page = await context.newPage();
    await vigilar(page, `portal-${vista}`);
    await iniciarSesion(page, vista, cliente.usuario, cliente.password);
    await page.goto(`${BASE}/portal`);
    await esperar(page);
    await capturar(page, `${vista}-portal`);
    console.log(`${vista}-portal  (${page.url().replace(BASE, '')})`);
    await context.close();
  }
}

// Catálogo público: lo publica por la API y lo fotografía sin sesión (lista y ficha de un proyecto).
async function catalogo(browser) {
  const admin = (await api('POST', '/auth/login', null, { identificador: USUARIO, password: PASSWORD })).json?.accessToken;
  const estado = (await api('PUT', '/empresas/actual/catalogo', admin, { publico: true })).json;
  if (!estado?.codigo) return;
  const proyectos = (await api('GET', `/publico/catalogo/${estado.codigo}`)).json?.proyectos ?? [];
  const rutas = [`catalogo/${estado.codigo}`, ...(proyectos[0] ? [`catalogo/${estado.codigo}/proyecto/${proyectos[0].id}`] : [])];
  for (const [vista, viewport] of [['escritorio', { width: 1440, height: 900 }], ['movil', { width: 390, height: 844 }]]) {
    const context = await browser.newContext({ viewport, deviceScaleFactor: 1, locale: 'es-CO', timezoneId: 'America/Bogota' });
    const page = await context.newPage();
    await vigilar(page, `catalogo-${vista}`);
    for (const [i, ruta] of rutas.entries()) {
      await page.goto(`${BASE}/${ruta}`);
      await esperar(page);
      await capturar(page, `${vista}-catalogo-${i + 1}`);
      console.log(`${vista}-catalogo-${i + 1}  (${page.url().replace(BASE, '')})`);
    }
    await context.close();
  }
}

const browser = await chromium.launch();
try {
  await recorrer(browser, 'escritorio', { width: 1440, height: 900 }, RUTAS);
  await recorrer(browser, 'movil', { width: 390, height: 844 }, MOVIL);
  const cliente = await prepararCliente().catch((e) => {
    errores.push(`[portal] ${e.message}`);
    return null;
  });
  if (cliente) await portal(browser, cliente);
  await catalogo(browser).catch((e) => errores.push(`[catalogo] ${e.message}`));
} finally {
  await browser.close();
  writeFileSync(`${SALIDA}/errores.txt`, errores.length ? errores.join('\n') + '\n' : 'Sin errores\n');
}
console.log(`${errores.length} errores en el navegador (ver ${SALIDA}/errores.txt)`);
