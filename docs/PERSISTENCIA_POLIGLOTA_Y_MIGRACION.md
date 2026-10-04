# Persistencia políglota y migración de Nexora Core

## 1. Objetivo

Incorporar MongoDB como almacenamiento documental principal de Nexora Core sin
interrumpir el sistema actual, conservar MySQL para las operaciones que todavía
dependen del modelo relacional y utilizar Redis exclusivamente para información
temporal. La migración se ejecutará por módulos y con posibilidad de reversión.

## 2. Responsabilidad de cada tecnología

| Tecnología | Responsabilidad | No debe utilizarse para |
|---|---|---|
| MongoDB | Modelo documental principal, agregados de negocio migrados y consultas orientadas a documentos. | Caché desechable o duplicar tablas sin rediseñarlas. |
| MySQL | Sistema actual, referencia de comparación y componentes transaccionales aún no migrados. | Recibir nuevas estructuras que ya hayan sido declaradas nativas de MongoDB. |
| Redis | Caché con vencimiento, idempotencia, rate limiting, bloqueos distribuidos y datos efímeros. | Ser la única copia de clientes, contratos, pagos o documentos. |

Mientras un módulo no haya superado las pruebas de migración, MySQL sigue
siendo su fuente de verdad. Declarar MongoDB como almacenamiento principal es
el estado objetivo, no una autorización para cambiar todos los repositorios en
una sola entrega.

## 3. Herramientas y fuente de verdad

### Moon Modeler

Se utiliza para el diseño visual y documental del destino:

- colecciones y campos;
- documentos y arreglos embebidos;
- referencias entre colecciones;
- tipos BSON y campos obligatorios;
- validaciones `$jsonSchema`;
- índices simples, compuestos y únicos;
- exportación del diagrama, documentación y scripts.

El archivo editable debe guardarse como
`migration/moon-modeler/nexora-mongodb-vNN.dmm`. El script generado se guarda
en `migration/schema/` y nunca debe contener contraseñas.

### MongoDB Relational Migrator

Se utiliza para convertir el diseño en un mapeo ejecutable y transferir los
datos desde MySQL:

- analiza el esquema relacional;
- permite aplicar reglas de unión, separación, exclusión y embebido;
- ejecuta el trabajo snapshot;
- muestra errores y métricas de la carga;
- permite exportar el proyecto como archivo `.relmig`.

Cada versión aprobada se guarda como
`migration/relational-migrator/nexora-vNN.relmig`. El archivo no contiene las
contraseñas de las conexiones.

### Regla de sincronización del diseño

Moon Modeler describe el modelo aprobado y Relational Migrator describe cómo
se construye desde MySQL. Una modificación no queda terminada hasta que se
actualizan el `.dmm`, el `.relmig`, los scripts de validación y la matriz de
pruebas de la misma versión.

## 4. Estrategia paso a paso

### Fase 0. Línea base

1. Crear una copia de seguridad verificable de MySQL.
2. Registrar el número de tablas, filas por tabla y migraciones Flyway.
3. Clasificar datos sensibles, históricos, catálogos y transacciones.
4. Inventariar consultas reales del backend y del dashboard.
5. No cambiar código ni datos durante la captura de la línea base.

**Entregable:** inventario de origen y reporte de riesgos.

### Fase 1. Modelo objetivo en Moon Modeler

Para cada módulo:

1. Definir las consultas que debe resolver.
2. Escoger la raíz del agregado.
3. Embeber datos que pertenecen al mismo ciclo de vida y tienen crecimiento
   limitado.
4. Referenciar datos compartidos, independientes o con crecimiento ilimitado.
5. Conservar `empresaId` en todas las colecciones multiempresa.
6. Diseñar índices empezando por `empresaId` y los filtros frecuentes.
7. Definir tipos BSON, requeridos, enumeraciones, rangos y validaciones.
8. Exportar el archivo `.dmm`, el diagrama y el script de creación.

**Entregable:** diseño documental aprobado y versionado.

### Fase 2. Mapeo en Relational Migrator

1. Conectar el origen. Desde Relational Migrator instalado en Windows use
   `jdbc:mysql://localhost:3306/inmobiliaria_db`; desde el perfil Docker use
   `jdbc:mysql://mysql:3306/inmobiliaria_db`.
2. Usar el usuario `MYSQL_MIGRATOR_USER`, nunca el usuario root.
3. Conectar el destino. Desde Windows use
   `mongodb://localhost:27017/nexora?authSource=admin`; desde el perfil Docker
   use `mongodb://mongodb:27017/nexora?authSource=admin`.
4. Usar `MONGO_MIGRATOR_USER`, nunca el usuario root ni el usuario de la API.
5. Ejecutar Pre-Migration Analysis y guardar el informe.
6. Seleccionar únicamente las tablas del módulo piloto.
7. Reproducir las decisiones aprobadas en Moon Modeler como reglas de mapeo.
8. Revisar campos calculados, claves, valores nulos, fechas, decimales y JSON.
9. Exportar el proyecto `.relmig` antes de ejecutar la carga.

**Entregable:** mapeo ejecutable y análisis previo.

### Fase 3. Migración piloto

El primer piloto recomendado es **proyectos e inventario**, porque permite
validar empresa, proyecto, etapa, manzana, lote e historiales sin comenzar por
pagos o autenticación.

1. Migrar a una base de pruebas vacía.
2. Activar la opción idempotente o limpiar las colecciones del piloto antes de
   repetir el trabajo.
3. Aplicar validadores e índices generados desde el modelo aprobado.
4. No dirigir todavía la API productiva a las colecciones nuevas.

**Entregable:** base piloto reproducible.

### Fase 4. Validación

Se deben aprobar cuatro niveles:

| Nivel | Prueba mínima |
|---|---|
| Infraestructura | MySQL, MongoDB, Redis y API aparecen `UP`. |
| Volumen | Totales de origen y documentos destino coinciden según la regla de agrupación. |
| Contenido | Muestreo de identificadores, dinero, fechas, estados y relaciones. |
| Negocio | Una empresa no puede consultar datos de otra; inventario y dashboards mantienen resultados equivalentes. |

Además se prueban documentos inválidos, índices únicos, reejecución del trabajo,
tiempos de consulta y recuperación desde la copia de seguridad.

### Fase 5. Adaptación de la aplicación

1. Crear documentos y repositorios MongoDB del módulo piloto.
2. Mantener contratos DTO y endpoints para no romper Angular.
3. Implementar lectura comparativa MySQL/MongoDB solo en pruebas.
4. Habilitar MongoDB mediante una bandera por módulo.
5. Observar errores y diferencias antes de cambiar las escrituras.
6. Migrar después CRM, ventas, servicio y finalmente las áreas financieras o
   de seguridad que hayan sido aprobadas para MongoDB.

### Fase 6. Corte y reversión

1. Programar una ventana de corte para el snapshot final.
2. Detener temporalmente las escrituras del módulo.
3. Ejecutar el trabajo final y la matriz automática de validación.
4. Habilitar MongoDB como fuente del módulo.
5. Conservar MySQL en modo de referencia durante el periodo acordado.
6. Si falla cualquier criterio, desactivar la bandera y volver a MySQL.

## 5. Inicio y verificación con Docker

```bash
cp .env.example .env
```

Cambiar todas las credenciales y luego ejecutar:

```bash
docker compose up --build -d
./scripts/provision-migrator-users.sh
./scripts/verify-datastores.sh
```

El aprovisionamiento explícito también funciona cuando ya existe un volumen de
MySQL creado por una versión anterior de Nexora y, por tanto, los scripts de
inicialización de la imagen no vuelven a ejecutarse automáticamente.

Para iniciar también Relational Migrator en Docker, ejecute:

```bash
docker compose --profile migration up --build -d relational-migrator
./scripts/verify-migrator.sh
```

La imagen local incorpora automáticamente MySQL Connector/J durante su
construcción, porque la imagen oficial de Relational Migrator no lo incluye.

Servicios locales:

| Servicio | Dirección |
|---|---|
| Aplicación web | `http://localhost:4200` |
| API | `http://localhost:8080` |
| MySQL para Migrator | `localhost:3306` |
| MongoDB para Migrator | `localhost:27017` |
| Redis | `localhost:6379` |
| Relational Migrator opcional | `http://localhost:8278` |

Las credenciales están únicamente en `.env`. No se deben guardar dentro de los
archivos `.dmm`, `.relmig`, scripts, capturas o documentos de entrega.

## 6. Criterios para considerar migrado un módulo

Un módulo solo cambia su fuente de datos cuando:

- el diseño de Moon Modeler fue aprobado;
- el proyecto de Relational Migrator fue exportado;
- la migración puede repetirse sin duplicados;
- validadores e índices fueron aplicados;
- conteos y reglas de negocio coinciden;
- las pruebas multiempresa pasan;
- el rendimiento cumple el objetivo definido;
- existe copia de seguridad y procedimiento de reversión;
- frontend y API pasan sus pruebas funcionales.

## 7. Orden recomendado para Nexora

1. Proyectos e inventario.
2. CRM y simulaciones.
3. Clientes y portal.
4. Servicio, soporte y notificaciones.
5. Reservas y contratos.
6. Gerencia, reportes y auditoría.
7. Cartera y pagos, solo después de pruebas financieras y de concurrencia.
8. Seguridad e identidad, únicamente si la arquitectura final justifica moverlas.
