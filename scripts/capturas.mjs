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
  'ventas',
  'cartera',
  'servicio',
  'gerencia',
  'auditoria',
  'seguridad/cambiar-password',
];
const MOVIL = ['dashboard', 'inventario', 'cartera'];
const SALIDA = 'capturas';
mkdirSync(SALIDA, { recursive: true });

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

async function iniciarSesion(page) {
  await page.goto(`${BASE}/login`);
  await esperar(page);
  await page.screenshot({ path: `${SALIDA}/00-login.png`, fullPage: true });
  await page.locator('[formcontrolname="identificador"]').fill(USUARIO);
  await page.locator('[formcontrolname="password"]').fill(PASSWORD);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 });
  await esperar(page);
}

async function recorrer(browser, vista, viewport, rutas) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 1, locale: 'es-CO' });
  const page = await context.newPage();
  await vigilar(page, vista);
  await iniciarSesion(page);
  let i = 1;
  for (const ruta of rutas) {
    await page.goto(`${BASE}/${ruta}`);
    await esperar(page);
    const archivo = `${SALIDA}/${vista}-${String(i++).padStart(2, '0')}-${nombre(ruta)}.png`;
    await page.screenshot({ path: archivo, fullPage: true });
    console.log(`${archivo}  (${page.url().replace(BASE, '')})`);
  }
  if (vista === 'movil') {
    const menu = page.getByRole('button', { name: 'Abrir menú' });
    if (await menu.isVisible()) {
      await menu.click();
      await page.waitForTimeout(400);
      await page.screenshot({ path: `${SALIDA}/movil-menu.png` });
    }
  }
  await context.close();
}

const browser = await chromium.launch();
try {
  await recorrer(browser, 'escritorio', { width: 1440, height: 900 }, RUTAS);
  await recorrer(browser, 'movil', { width: 390, height: 844 }, MOVIL);
} finally {
  await browser.close();
  writeFileSync(`${SALIDA}/errores.txt`, errores.length ? errores.join('\n') + '\n' : 'Sin errores\n');
}
console.log(`${errores.length} errores en el navegador (ver ${SALIDA}/errores.txt)`);
