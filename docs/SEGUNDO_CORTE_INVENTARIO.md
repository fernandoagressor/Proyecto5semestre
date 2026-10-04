# Segundo corte: Proyectos e Inventario

Fecha: 31 de julio de 2026

## Objetivo

Implementar el primer módulo operativo completo sobre el núcleo de seguridad:

```text
Empresa → Proyecto → Etapa → Manzana → Lote
```

El módulo utiliza las tablas normalizadas existentes; no crea un modelo
paralelo ni recupera la entidad antigua `Propiedad`.

## Alcance implementado

- Alta, consulta, edición y cambio de estado de proyectos.
- Alta, consulta y edición de etapas.
- Alta, consulta y edición de manzanas.
- Alta, consulta paginada y edición de lotes.
- Filtros de lotes por estado, etapa, manzana y texto.
- Cambio manual controlado de disponibilidad.
- Cambio de precio con motivo e historial.
- Historial de estados.
- Resumen de unidades y valor disponible.
- Validación del límite de proyectos contratado por la empresa.
- Validación del máximo de lotes configurado por proyecto.
- Validación de códigos y números únicos.
- Validación jerárquica de empresa, proyecto, etapa, manzana y lote.
- Validación de gerente activo perteneciente a la misma empresa.

No se implementaron eliminaciones físicas. Los registros operativos se
conservan para trazabilidad.

## Seguridad y multiempresa

La autorización tiene dos capas:

1. `@PreAuthorize` comprueba la capacidad específica del usuario.
2. `AlcanceProyectoService` comprueba el ámbito vigente guardado en
   `usuario_alcances`.

Los alcances admitidos son:

- `EMPRESA`: todos los proyectos de la empresa.
- `PROYECTO`: únicamente proyectos asignados.
- `PROPIOS`: proyectos donde el usuario es el gerente responsable.

El identificador de empresa se toma del JWT. El navegador nunca puede elegir
libremente otra empresa mediante un parámetro.

La migración `V10` asigna alcance empresarial a administradores existentes y
el bootstrap lo asigna al crear el primer administrador.

## Endpoints y permisos

| Método | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/proyectos` | `PROYECTOS_VER` |
| `POST` | `/api/v1/proyectos` | `PROYECTOS_CREAR` |
| `GET` | `/api/v1/proyectos/{proyectoId}` | `PROYECTOS_VER` |
| `PUT` | `/api/v1/proyectos/{proyectoId}` | `PROYECTOS_EDITAR` |
| `PATCH` | `/api/v1/proyectos/{proyectoId}/estado` | `PROYECTOS_EDITAR` |
| `GET` | `/api/v1/proyectos/{proyectoId}/etapas` | `ETAPAS_VER` |
| `POST` | `/api/v1/proyectos/{proyectoId}/etapas` | `ETAPAS_GESTIONAR` |
| `PUT` | `/api/v1/proyectos/{proyectoId}/etapas/{etapaId}` | `ETAPAS_GESTIONAR` |
| `GET` | `/api/v1/proyectos/{proyectoId}/etapas/{etapaId}/manzanas` | `MANZANAS_VER` |
| `POST` | `/api/v1/proyectos/{proyectoId}/etapas/{etapaId}/manzanas` | `MANZANAS_GESTIONAR` |
| `PUT` | `/api/v1/proyectos/{proyectoId}/etapas/{etapaId}/manzanas/{manzanaId}` | `MANZANAS_GESTIONAR` |
| `GET` | `/api/v1/proyectos/{proyectoId}/lotes` | `LOTES_VER` o `LOTES_DISPONIBILIDAD_VER` |
| `POST` | `/api/v1/proyectos/{proyectoId}/lotes` | `LOTES_CREAR` |
| `GET` | `/api/v1/proyectos/{proyectoId}/lotes/{loteId}` | `LOTES_VER` |
| `PUT` | `/api/v1/proyectos/{proyectoId}/lotes/{loteId}` | `LOTES_EDITAR` |
| `PATCH` | `/api/v1/proyectos/{proyectoId}/lotes/{loteId}/estado` | `LOTES_CAMBIAR_ESTADO` |
| `PATCH` | `/api/v1/proyectos/{proyectoId}/lotes/{loteId}/precio` | `PRECIOS_LOTE_CAMBIAR` |
| `GET` | `/api/v1/proyectos/{proyectoId}/lotes/{loteId}/historial-estados` | `LOTES_VER` |
| `GET` | `/api/v1/proyectos/{proyectoId}/lotes/{loteId}/historial-precios` | `LOTES_VER` |
| `GET` | `/api/v1/proyectos/{proyectoId}/inventario/resumen` | `LOTES_VER` o `LOTES_DISPONIBILIDAD_VER` |

## Reglas de integridad

- Solo un usuario con alcance `EMPRESA` puede crear proyectos.
- Un proyecto `FINALIZADO` o `INACTIVO` no admite cambios de inventario.
- Solo se crean lotes dentro de etapas y manzanas activas.
- Un lote reservado o vendido no admite edición de datos físicos.
- Un lote reservado o vendido no admite cambios de precio.
- `RESERVADO` y `VENDIDO` no se asignan manualmente; los establecerán los
  módulos de reservas y contratos.
- Los cambios manuales solo permiten `DISPONIBLE` y `BLOQUEADO`.
- El precio inicial y cada modificación quedan registrados.
- Cada cambio de estado queda registrado con usuario, origen y motivo.
- La columna `disponible` debe coincidir con el estado del lote.
- La base de datos permite un solo precio vigente por lote.

## Concurrencia

- `Lote.version` utiliza bloqueo optimista.
- Los cambios de estado, precio y datos físicos recuperan el lote con
  `PESSIMISTIC_WRITE`.
- Los índices únicos de MySQL protegen códigos y números aun cuando dos
  solicitudes concurrentes superen la validación previa.
- Las violaciones de integridad se devuelven como HTTP `409`.

## Ejemplo: crear lote

```json
{
  "etapaId": 1,
  "manzanaId": 4,
  "codigo": "MZ-A-L01",
  "numero": "01",
  "areaM2": 120.00,
  "frenteM": 8.00,
  "fondoM": 15.00,
  "valorLista": 120000000.00,
  "ubicacionReferencia": "Esquina nororiental",
  "descripcion": "Lote residencial"
}
```

## Ejemplo: bloquear lote

```json
{
  "estado": "BLOQUEADO",
  "motivo": "Revisión jurídica del folio"
}
```

## Ejemplo: cambiar precio

```json
{
  "valor": 125000000.00,
  "motivo": "Actualización de lista de precios de agosto"
}
```

## Pruebas incorporadas

- Transiciones manuales permitidas y prohibidas del lote.
- Sincronización entre estado y disponibilidad.
- Cambio de precio y rechazo de valores inválidos.
- Validación cronológica del proyecto.
- Reglas de alcance por empresa, proyecto y registros propios.
- Consulta de proyecto limitada a la empresa autenticada.

## Validación de este entorno

- Los 82 archivos Java pasan análisis sintáctico.
- Las entidades del módulo apuntan a tablas y columnas existentes.
- Los permisos usados por los controladores existen en la migración de datos
  iniciales.
- Las diez migraciones conservan orden Flyway.

La compilación Maven, el arranque Spring y las pruebas contra MySQL 8.4 deben
ejecutarse en un entorno con JDK 21, Maven 3.9 y MySQL/Docker disponibles.
