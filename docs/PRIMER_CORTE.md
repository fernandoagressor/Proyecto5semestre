# Primer corte de desarrollo

Fecha: 31 de julio de 2026

## Objetivo

Crear una base ejecutable, segura y coherente con el modelo normalizado antes de
desarrollar los módulos operativos.

## Alcance entregado

### Persistencia

- Migraciones Flyway `V1` a `V9`.
- 95 tablas.
- 260 claves foráneas.
- 129 permisos.
- 8 roles base.
- Configuración MySQL 8.4 y zona horaria `America/Bogota`.
- Validación JPA sin creación automática de tablas.

### Seguridad

- Contraseñas BCrypt con costo 12.
- JWT HS256 de corta duración.
- Validación de firma, expiración e issuer.
- Refresh tokens aleatorios de 512 bits.
- En base de datos se guarda únicamente SHA-256 del refresh token.
- Rotación del refresh token con bloqueo pesimista para evitar doble uso
  simultáneo.
- Revocación de sesión al cerrar sesión.
- Registro de cada intento de acceso.
- Bloqueo temporal tras cinco intentos fallidos.
- Roles y permisos emitidos como autoridades.
- Autorización por método con `@PreAuthorize`.
- CORS limitado a orígenes configurados.
- Configuración sin credenciales reales dentro del repositorio.

### Multiempresa

- Cada usuario interno queda asociado a una empresa.
- El token incluye `empresa_id`.
- Los endpoints empresariales obtienen el alcance desde el token, no desde un
  identificador libre enviado por el navegador.
- El rol interno `SUPER_ADMIN` se conserva para operación de plataforma, pero
  no forma parte de la interfaz de la empresa cliente.

## Endpoints

| Método | Ruta | Autenticación | Uso |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Pública | Iniciar sesión |
| `POST` | `/api/v1/auth/refresh` | Refresh token | Renovar y rotar sesión |
| `POST` | `/api/v1/auth/logout` | Bearer | Revocar sesión |
| `GET` | `/api/v1/auth/me` | Bearer | Consultar identidad y autoridades |
| `GET` | `/api/v1/empresas/actual` | Bearer + permiso | Consultar empresa propia |
| `GET` | `/actuator/health` | Pública | Salud del despliegue |

## Criterios verificados

- Los archivos Java pasan análisis sintáctico.
- Cada entidad implementada apunta a una tabla y columnas existentes.
- Los checksums de las nueve migraciones coinciden con el paquete normalizado
  aprobado.
- El nuevo repositorio no contiene referencias a `terracore`, `companies` ni a
  la base de datos anterior.
- No existen contraseñas reales ni secretos codificados.

## Validación pendiente en infraestructura

Este entorno de trabajo no dispone de JDK 21, Maven, Docker ni MySQL. Por ello,
la ejecución de `mvn clean verify`, el arranque de los contenedores y la prueba
HTTP contra MySQL 8.4 deben realizarse en un equipo o pipeline con esas
herramientas.

## Siguiente corte propuesto

Módulo de proyectos e inventario:

1. Proyectos.
2. Etapas.
3. Manzanas.
4. Lotes.
5. Historial de cambios de lote.
6. Disponibilidad transaccional.
7. Pruebas de aislamiento entre empresas.

