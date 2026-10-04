# Nexora Core Frontend

Aplicación web de NEXORA CORE para el ERP inmobiliario multiempresa.

## Base técnica

- Angular 20.3 con componentes standalone.
- Angular Material 20.2 y SCSS.
- TypeScript 5.9, RxJS y detección de cambios con Zone.js.
- Enrutamiento lazy, sin SSR/SSG y sin zoneless.
- Integración REST con `inmobiliaria-api` mediante `/api/v1`.

## Desarrollo local

La API debe estar disponible en `http://localhost:8080`.

```bash
npm ci
npm start
```

Abrir `http://localhost:4200`. El proxy local dirige `/api` hacia Spring Boot.

## Verificación

```bash
npm run build
npm run test:ci
npm audit
```

## Estructura

```text
src/app/
├── core/       modelos, servicios, guardas, interceptor y navegación
├── features/   autenticación, superadministración, dashboards, inventario y CRM
├── layout/     shell, sidebar y barra superior
└── shared/     identidad visual e iconos locales
```

La sesión utiliza access token JWT y refresh token rotativo. Los tokens se
almacenan en `sessionStorage`, nunca en cookies no controladas ni en archivos.
El menú y las rutas se filtran con las autoridades emitidas por el backend.

## Operación disponible

- Inventario por proyecto con indicadores, filtros y paginación.
- Creación de etapas, manzanas y lotes.
- Cambios trazables de precio y disponibilidad.
- Pipeline comercial con avance controlado de oportunidades.
- Registro y calificación de prospectos.
- Agenda comercial con validación de cruces en el backend.
- Simulaciones financieras sobre lotes disponibles.
- Consola global exclusiva de `SUPER_ADMIN` para empresas, proyectos,
  usuarios, roles, permisos y alcances.
- Cambio seguro de empresa activa con rotación del refresh token.
- Entrada directa desde la empresa seleccionada hacia su dashboard o
  inventario operativo.

Las pantallas ocultan acciones no autorizadas para mejorar la experiencia, pero
la autorización final y el alcance multiempresa siempre los resuelve la API.
