# Arquitectura del backend por capas

## Objetivo

Nexora Core organiza el backend Spring Boot mediante capas técnicas visibles,
sin perder la separación entre los módulos de negocio. La clase principal se
mantiene en `com.inmobiliaria.inmobiliaria_api`, de modo que el escaneo de
componentes, entidades, repositorios y propiedades cubra todas las capas.

## Estructura

```text
src/main/java/com/inmobiliaria/inmobiliaria_api/
├── config/
│   ├── BootstrapAdminProperties.java
│   └── SecurityProperties.java
├── controller/
│   ├── administracion/
│   ├── cartera/
│   ├── crm/
│   ├── inventario/
│   ├── servicio/
│   └── ventas/
├── dto/
│   ├── cartera/
│   ├── crm/
│   ├── inventario/
│   ├── servicio/
│   └── ventas/
├── entity/
│   ├── cartera/
│   ├── crm/
│   ├── inventario/
│   ├── seguridad/
│   └── ventas/
├── repository/
│   ├── cartera/
│   ├── crm/
│   ├── inventario/
│   ├── seguridad/
│   └── ventas/
├── security/
│   ├── SecurityConfig.java
│   ├── JwtService.java
│   ├── PasswordChangeRequiredFilter.java
│   └── ContextoUsuario.java
├── service/
│   ├── administracion/
│   ├── cartera/
│   ├── crm/
│   ├── inventario/
│   ├── servicio/
│   └── ventas/
└── InmobiliariaApiApplication.java
```

El árbol anterior resume los módulos principales. También se conservan
`cliente`, `empresa`, `gerencia`, `notificacion`, `portal`, `simulacion` y
`soporte` dentro de las capas que les correspondan.

## Responsabilidad de cada capa

| Capa | Responsabilidad | No debe hacer |
|---|---|---|
| `config` | Propiedades tipadas y configuración transversal no relacionada con autenticación. | Ejecutar reglas comerciales. |
| `controller` | Recibir HTTP, validar DTO, identificar al usuario y delegar al servicio. | Consultar repositorios directamente. |
| `dto` | Definir solicitudes y respuestas de la API. | Persistirse como entidades JPA. |
| `entity` | Representar entidades, estados y relaciones persistentes. | Conocer controladores o detalles HTTP. |
| `repository` | Consultar y guardar información mediante Spring Data JPA. | Coordinar procesos completos de negocio. |
| `security` | Configurar Spring Security, JWT, filtros y contexto autenticado. | Implementar reglas comerciales de módulos. |
| `service` | Ejecutar casos de uso, transacciones, validaciones y reglas de aplicación. | Exponer rutas HTTP. |

## Flujo obligatorio

```text
Angular → Controller → DTO → Service → Repository → Entity → MySQL
```

El flujo de respuesta regresa desde el servicio mediante un DTO. Una entidad
JPA no se devuelve directamente desde un controlador.

## Convenciones

1. Los controladores terminan en `Controller`.
2. Los servicios terminan en `Service`; los procesos automáticos pueden
   terminar en `Job`.
3. Las interfaces de persistencia terminan en `Repository`.
4. Los contratos HTTP terminan en `Request`, `Response` o `Dtos`.
5. Toda consulta multiempresa conserva `empresaId` o `ContextoUsuario`.
6. Los permisos continúan aplicándose mediante `@PreAuthorize`.
7. `SUPER_ADMIN` mantiene el acceso global; los demás usuarios permanecen
   limitados a su empresa activa.
8. Las migraciones Flyway permanecen en `src/main/resources/db/migration`.

## Trazabilidad de la reorganización

`REORGANIZACION_PAQUETES.json` registra la ruta anterior y la ruta nueva de
cada clase. La reorganización modifica paquetes e importaciones, pero conserva
los cuerpos de las 396 clases productivas, las 34 pruebas existentes, las rutas
REST, las migraciones y el frontend. Además, `ArquitecturaCapasTest` incorpora
cuatro verificaciones para impedir que reaparezcan los paquetes anteriores o
dependencias inválidas entre capas.
