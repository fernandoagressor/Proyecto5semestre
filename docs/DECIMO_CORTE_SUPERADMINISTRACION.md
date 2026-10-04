# Décimo corte — Consola de Superadministración Multiempresa

## Objetivo

Convertir el rol `SUPER_ADMIN` en una consola global real para crear y gobernar
empresas, proyectos, usuarios, roles, permisos y alcances sin ejecutar SQL
manual. La autorización efectiva continúa siendo responsabilidad del backend:
rol + permiso + empresa + proyecto + propiedad del registro.

## Consola Angular

La ruta protegida `/superadmin` contiene cuatro áreas:

### Empresas

- Resumen global de empresas, proyectos y usuarios.
- Búsqueda por código, nombre, NIT o ciudad.
- Filtro por estado `ACTIVA`, `SUSPENDIDA` o `INACTIVA`.
- Creación transaccional de empresa y administrador inicial.
- Edición de datos, zona horaria, moneda y límites del plan.
- Activación, suspensión e inactivación con confirmación.
- Control optimista para evitar sobrescrituras concurrentes.
- Revocación de sesiones ordinarias al restringir una empresa.

Una empresa con proyectos operativos no se puede inactivar; debe suspenderse o
cerrar primero esos proyectos.

### Proyectos

- Consulta del portafolio dentro de la empresa seleccionada.
- Creación y edición de proyectos.
- Validación de los límites definidos para la empresa.
- Entrada directa al inventario para continuar con etapas, manzanas y lotes.

### Usuarios

- Consulta y búsqueda de usuarios internos por empresa.
- Creación con contraseña temporal obligatoria.
- Asignación de uno o varios roles permitidos.
- Alcance de empresa completa, proyecto específico o registros propios.
- Edición de datos, roles y alcance.
- Activación, bloqueo e inactivación.
- Reinicio de credenciales con revocación de sesiones y cambio obligatorio de
  contraseña.
- Protección de la cuenta `SUPER_ADMIN` frente a cambios desde una empresa.

### Roles y permisos

- Catálogo de roles del sistema disponible para la empresa.
- Roles protegidos de solo lectura.
- Creación y edición de roles personalizados aislados por empresa.
- Selección de permisos agrupados por módulo.
- Identificación de permisos sensibles.

## Cambio de espacio de trabajo

`POST /api/v1/auth/contexto` permite únicamente a `SUPER_ADMIN` entrar a una
empresa activa. La operación:

1. valida que la empresa exista y esté activa;
2. garantiza el alcance empresarial del superadministrador;
3. guarda `empresa_contexto_id` en la sesión renovable;
4. rota el refresh token;
5. emite un nuevo JWT con el `empresa_id` seleccionado;
6. registra el cambio en la auditoría inmutable.

Al renovar el token, la empresa elegida se conserva. El shell Angular actualiza
el nombre del espacio de trabajo sin cerrar la sesión.

## Migración V16

`V16__consola_superadministracion_multiempresa.sql` es acumulativa y agrega:

- `sesiones_usuario.empresa_contexto_id`;
- versión optimista en `empresas` y `usuarios`;
- permisos sensibles para activar/suspender empresas, reiniciar credenciales y
  cambiar el contexto;
- asignación de estos permisos al rol global `SUPER_ADMIN`;
- alcance `EMPRESA` del superadministrador sobre todas las empresas existentes.

Las migraciones `V1–V15` permanecen idénticas al noveno corte.

## Endpoints nuevos

```text
POST  /api/v1/auth/contexto

GET   /api/v1/admin/resumen
GET   /api/v1/admin/empresas
GET   /api/v1/admin/empresas/{empresaId}
POST  /api/v1/admin/empresas
PUT   /api/v1/admin/empresas/{empresaId}
PATCH /api/v1/admin/empresas/{empresaId}/estado

GET   /api/v1/admin/empresas/{empresaId}/usuarios
POST  /api/v1/admin/empresas/{empresaId}/usuarios
PUT   /api/v1/admin/empresas/{empresaId}/usuarios/{usuarioId}
PATCH /api/v1/admin/empresas/{empresaId}/usuarios/{usuarioId}/estado
POST  /api/v1/admin/empresas/{empresaId}/usuarios/{usuarioId}/credenciales

GET   /api/v1/admin/empresas/{empresaId}/roles
POST  /api/v1/admin/empresas/{empresaId}/roles
PUT   /api/v1/admin/empresas/{empresaId}/roles/{rolId}
GET   /api/v1/admin/permisos

GET   /api/v1/admin/empresas/{empresaId}/proyectos
POST  /api/v1/admin/empresas/{empresaId}/proyectos
PUT   /api/v1/admin/empresas/{empresaId}/proyectos/{proyectoId}
PATCH /api/v1/admin/empresas/{empresaId}/proyectos/{proyectoId}/estado
```

Todas las rutas `/api/v1/admin/**` requieren `ROLE_SUPER_ADMIN` en el backend.

## Bootstrap inicial

En una instalación limpia, `BootstrapAdminService` asigna ahora el rol global
`SUPER_ADMIN` al usuario inicial. Ese usuario recibe alcance sobre su empresa
inicial y sobre las empresas que cree posteriormente.

## Validación del corte

- 396 archivos Java analizados sintácticamente sin fallos.
- 16 migraciones Flyway y 96 tablas.
- 223 rutas HTTP únicas; no hay rutas duplicadas.
- Todas las autoridades Java existen en el catálogo Flyway.
- Checksums de `V1–V15` idénticos al noveno corte.
- Compilación Angular de producción aprobada.
- 15/15 pruebas Angular aprobadas en siete archivos.
- Cero vulnerabilidades reportadas por `npm audit`.
- El módulo de Superadministración permanece en un chunk lazy independiente.

El entorno de generación no dispone de JDK 21, Maven ni MySQL, por lo que la
compilación integral del backend y la ejecución real de `V16` deben realizarse
en la estación local ya preparada para NEXORA CORE.

## Puesta en marcha local

1. Sustituir el proyecto anterior por este corte conservando el `.env` local.
2. Iniciar Spring Boot; Flyway aplicará únicamente `V16`.
3. Iniciar Angular con `npm start`.
4. Ingresar nuevamente como `superadmin`.
5. Abrir **Superadministración** en el menú lateral.

