# Undécimo corte: seguridad de contraseña y permisos

Este corte se construyó sobre la versión estable entregada por el usuario. Las
migraciones `V1` a `V16` se conservan sin modificaciones. El único cambio de
esquema es `V17__seguridad_password_y_permisos.sql`.

## Alcance incorporado

- Cambio de contraseña desde el menú del usuario.
- Redirección obligatoria cuando la cuenta tiene una contraseña temporal.
- Bloqueo en backend de los módulos operativos mientras el cambio sea
  obligatorio.
- Verificación de la contraseña actual.
- Confirmación de la contraseña nueva.
- Política de 12 a 72 caracteres, con mayúscula, minúscula, número y símbolo,
  sin espacios.
- BCrypt con costo 12, usando la configuración de seguridad existente.
- Cierre de todas las sesiones renovables después del cambio.
- Incremento de `token_version` para invalidar inmediatamente los JWT emitidos
  antes del cambio.
- Invalidación equivalente cuando un administrador reinicia credenciales.
- Registro de auditoría sin almacenar contraseñas ni hashes.
- Corrección permanente del dashboard de los roles `ADMINISTRADOR` y
  `AUDITOR`.
- Reconciliación del catálogo completo de permisos para `SUPER_ADMIN`.

## Flujo de cambio obligatorio

1. El usuario inicia sesión con la contraseña temporal.
2. Angular lo dirige a `/seguridad/cambiar-password`.
3. El backend impide el acceso a módulos diferentes de seguridad y cierre de
   sesión.
4. El usuario ingresa la contraseña actual y la nueva contraseña dos veces.
5. El backend cambia el hash, elimina la marca
   `requiere_cambio_password`, incrementa `token_version`, revoca las sesiones
   y registra el evento.
6. El frontend elimina la sesión local y solicita iniciar sesión con la clave
   nueva.

## Matriz de dashboard cerrada

| Rol protegido | Dashboard principal |
| --- | --- |
| `SUPER_ADMIN` | Gerencial |
| `ADMINISTRADOR` | Gerencial |
| `ASESOR_COMERCIAL` | Comercial |
| `CARTERA` | Cartera |
| `GERENCIA` | Gerencial |
| `AUDITOR` | Gerencial de solo lectura funcional |
| `SERVICIO_CLIENTE` | Servicio |
| `CLIENTE` | Portal del cliente |

`V17` agrega `DASHBOARD_GERENCIAL_VER` a `ADMINISTRADOR` y `AUDITOR`. Los
demás roles ya tenían su permiso específico en `V9`. No es necesario volver a
ejecutar el script manual usado durante la prueba del décimo corte.

## Puesta en marcha sobre la base estable

1. Detener backend y frontend.
2. Sustituir el proyecto por este paquete completo.
3. Conservar la misma configuración de conexión a `inmobiliaria_db`.
4. Iniciar el backend. Flyway debe mostrar que aplica únicamente la versión
   `17` sobre el historial existente.
5. Iniciar Angular desde `frontend` con `npm start`, para conservar el proxy al
   puerto `8080`.
6. Ingresar con `superadmin` y con el usuario `fernandoj`.
7. Completar el cambio de contraseña cuando sea solicitado.
8. Comprobar que el dashboard gerencial cargue sin scripts manuales.

## Verificación esperada de Flyway

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

El último registro debe ser:

```text
17 | seguridad password y permisos | 1
```

## Endpoint agregado

```text
POST /api/v1/auth/cambiar-password
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "passwordActual": "clave-temporal",
  "passwordNueva": "NuevaClave#2026",
  "confirmacionPassword": "NuevaClave#2026"
}
```

Respuesta correcta: `204 No Content`. Todas las sesiones quedan invalidadas y
el usuario debe autenticarse nuevamente.

## Validaciones realizadas

- Compilación de producción del frontend.
- 18/18 pruebas unitarias del frontend aprobadas.
- Validación sintáctica de todo el código Java.
- Pruebas unitarias agregadas para el dominio de usuario, cambio de
  credenciales, versión de JWT y cliente HTTP Angular.
- Comparación de integridad para garantizar que `V1` a `V16` no cambiaron.
