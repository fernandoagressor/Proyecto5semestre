# Octavo corte — Frontend Angular base

## Objetivo

Iniciar la interfaz web de NEXORA CORE en el mismo repositorio
`inmobiliaria-api`, conectada al contrato REST real del backend y protegida por
los roles, permisos y alcances ya implementados.

## Stack fijado

- Angular 20.3.27.
- Angular CLI y build 20.3.33.
- Angular Material 20.2.14.
- TypeScript 5.9.
- Componentes standalone, SCSS y rutas lazy.
- Zone.js habilitado.
- Sin SSR, SSG ni zoneless.

Angular Material se mantiene en la línea 20 compatible con Angular 20. La
identidad visual usa azul profundo `#1E40AF`, grises, blanco y colores
semánticos, con modo claro y oscuro.

## Funcionalidad implementada

### Autenticación y sesión

- Inicio de sesión por correo o nombre de usuario.
- Integración exacta con `POST /api/v1/auth/login`.
- Access token JWT y refresh token rotativo.
- Renovación automática tras respuestas HTTP 401.
- Una sola renovación concurrente para evitar carreras.
- Cierre de sesión contra la API y limpieza local incluso si la red falla.
- Persistencia limitada a `sessionStorage`.
- Redirección segura a la ruta originalmente solicitada.
- Pantalla de sesión expirada y errores Problem Detail.

### Autorización

- Guarda de autenticación.
- Guarda de invitados.
- Guarda por uno o varios permisos.
- Menú generado únicamente con módulos autorizados.
- Rutas lazy protegidas en dos niveles: navegación e ingreso directo por URL.
- Página explícita de acceso restringido.

### Experiencia visual

- Login premium responsive.
- Shell con sidebar, barra superior y perfil.
- Identidad visual propia de NEXORA CORE sin fuentes o iconos remotos.
- Modo claro/oscuro persistente.
- Diseño responsive para escritorio, tableta y móvil.
- Estados de carga, vacío, error y reintento.
- Aviso cuando el backend exige cambio de contraseña.

### Dashboards

El frontend selecciona el dashboard permitido de mayor alcance y consulta datos
transaccionales reales:

- Gerencial: ventas, recaudo, cartera, utilidad e inventario.
- Comercial: embudo, conversión, reservas y ventas por asesor.
- Cartera: saldo, recaudo, mora, cuotas y edades de mora.
- Servicio: solicitudes, SLA, satisfacción, citas y tickets.
- Cliente: contratos, cuotas, saldo, documentos y solicitudes.

Incluye filtro por proyecto cuando el usuario tiene `PROYECTOS_VER`.

### Operación inicial

- Listado paginado de proyectos.
- Búsqueda con espera controlada y filtro por estado.
- Avance, ubicación y estado de cada proyecto.
- Centro de notificaciones con resumen y últimos cinco eventos sin leer.
- Espacios protegidos para Inventario, CRM, Ventas, Cartera, Servicio,
  Gerencia y Auditoría, alineados con los módulos del backend.

## Integración y ejecución

### Desarrollo

```bash
cd frontend
npm ci
npm start
```

El proxy de Angular dirige `/api` y `/actuator` hacia
`http://localhost:8080`.

### Docker Compose

```bash
docker compose up --build
```

- Frontend: `http://localhost:4200`.
- API: `http://localhost:8080`.
- MySQL: puerto `3306`.

Nginx sirve la aplicación SPA y actúa como proxy de la API dentro de Docker.

## Validación del corte

- Compilación Angular de producción completada.
- Paquete inicial: 312,23 kB sin comprimir, 88,08 kB estimados transferidos.
- Seis pruebas unitarias aprobadas con Vitest.
- Auditoría npm: cero vulnerabilidades conocidas.
- Quince migraciones Flyway verificadas contra el séptimo corte.
- La migración `V8`, que apareció truncada al restaurar el espacio de trabajo,
  fue recuperada exactamente desde el paquete original; su checksum vuelve a
  coincidir.

## Alcance pendiente del frontend

Este corte entrega el núcleo visual y de seguridad. La siguiente iteración del
frontend debe implementar los formularios, tablas y acciones detalladas de:

1. Inventario: etapas, manzanas, lotes, estados y precios.
2. CRM: prospectos, oportunidades, agenda y simulaciones.
3. Ventas: reservas, contratos, documentos y firmas.
4. Cartera: planes, cuotas, pagos, conciliación y mora.
5. Servicio: PQRS, citas, cambios de datos y soporte.
6. Gerencia: metas, comisiones, tareas, costos y reportes.
7. Auditoría: eventos, alertas y exportaciones.

También quedan pendientes pruebas E2E con el backend y MySQL reales, MFA y el
flujo de cambio de contraseña, porque esos últimos dos contratos todavía no
existen en la API.
