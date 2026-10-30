# Pruebas de carga y estrés (runbook)

Las pruebas usan [k6](https://k6.io) con usuarios reales por rol contra **staging** (nunca contra
producción). Se lanzan desde GitHub: **Actions → Prueba de carga → Run workflow**. Los scripts están
en `carga/`:

| Archivo | Qué hace |
| --- | --- |
| `carga/semilla.js` | Genera los datos de volumen por la API (una vez por servidor) |
| `carga/carga.js` | Los escenarios de carga: usuarios que recorren las pantallas de su rol |
| `.github/workflows/prueba-carga.yml` | Los corre contra staging y guarda el resultado; en cada PR que toque `carga/` los valida en el CI |

## 1. Criterios de aceptación (~100 usuarios concurrentes)

| Criterio | Meta | Dónde se ve |
| --- | --- | --- |
| p95 de lecturas | < 800 ms | Resumen de k6 y Grafana "p95 de la API" |
| p95 de escrituras | < 1,5 s | Resumen de k6 |
| Errores (5xx y fallas de red) | < 1 % | Resumen de k6 y Grafana "Errores 5xx" |
| CPU de la máquina | < 80 % sostenido | Grafana "CPU de la máquina" |
| Atraso de las tablas espejo (p95) | < 5 s | Grafana "Espejos: p95 de atraso" |
| Cola `crm_pendientes` | vuelve a 0 al terminar | Grafana "Cola crm_pendientes" |
| Resistencia de 1 hora | sin crecer la memoria ni los errores | Grafana, fila "Máquina y contenedores" |

El escenario **carga** falla en GitHub (rojo) si no cumple los tres primeros. En **estrés** y
**pico** se espera que falle en algún escalón: el objetivo es saber **en cuántos usuarios** se rompe
y por qué.

## 2. Preparación (una sola vez, con staging ya arriba)

1. En GitHub, **Settings → Environments → staging → Add secret**: `CARGA_PASSWORD`, una contraseña
   nueva de 12 o más caracteres con mayúscula, minúscula, número y símbolo. Es la de los usuarios de
   prueba `carga.<rol>.<NNN>`.
2. **Respaldo antes de tocar nada**, en el servidor: `cd /opt/nexora && bash deploy/respaldar.sh`.
3. **Datos de volumen**: Actions → Prueba de carga → Run workflow → acción `semilla`, volumen
   `completo`. Tarda unos 10 a 20 minutos y crea:
   - 10 proyectos con 2.000 lotes (20 % reservados con cliente, oportunidad y reserva);
   - 100 usuarios de prueba: 60 asesores, 20 de servicio, 10 de cartera y 10 de gerencia;
   - 2.000 clientes, 5.000 prospectos (la mitad con oportunidad), 4.000 tareas y 1.500 PQRS.

   Sube solo los límites de la empresa (proyectos y usuarios) si hacen falta.
4. **Respaldo de la base de pruebas**: `bash deploy/respaldar.sh`. Anote el nombre
   (`nexora-AAAAMMDD-HHMMSS`). Entre rondas se puede volver a esa base con
   `bash deploy/restaurar.sh /var/lib/nexora/respaldos/<nombre> --confirmar`, para comparar rondas en
   igualdad de condiciones.

> **En producción la empresa también arranca con límite de 1 proyecto y 5 usuarios.** Antes de la
> entrega hay que subirlos desde la consola de superadministración (Empresas → editar).

## 3. Cómo correr una ronda

**Antes**
- Abra Grafana por el túnel (`docs/DESPLIEGUE_AWS.md`, sección 9) en el tablero "NEXORA –
  Producción y pruebas de carga", rango "Last 30 minutes", actualización cada 10 s.
- Confirme que nadie más está usando staging y que no hay un despliegue en curso.
- Anote la versión desplegada (`grep NEXORA_VERSION /opt/nexora/.env`).

**Durante**: Actions → Prueba de carga → Run workflow → acción `prueba`, el escenario y volumen
`completo`.

| Escenario | Usuarios | Duración | Para qué |
| --- | --- | --- | --- |
| `humo` | 5 | 1 min | Comprobar que todo responde antes de una ronda grande |
| `carga` | 0 → 100, se mantiene 20 min | 27 min | La meta: 100 usuarios concurrentes |
| `estres` | 100 → 150 → 200 → 300 por escalones | 32 min | Encontrar el punto de quiebre |
| `pico` | de 10 a 200 en 30 s | 9 min | Un pico brusco (p. ej. apertura de ventas) |
| `resistencia` | 100 durante 1 hora | 67 min | Fugas de memoria, conexiones o colas que crecen |

**Cancele la ronda** (Cancel workflow) si los errores 5xx pasan del 20 % por más de 2 minutos o la
API se reinicia: el resultado ya no sirve y se arriesga la base.

**Después**
- El resumen queda en la página de la ejecución (Summary): criterios ✅/❌ y una tabla por petición
  ordenada de la más lenta a la más rápida. El JSON completo queda en el artefacto `resultados-*`.
- En Grafana fije el rango de la ronda y tome captura de la fila superior y de "Bases de datos".
- Revise en el servidor: `docker compose logs --since 40m api | grep -c ' ERROR '` y
  `docker compose ps` (ningún contenedor reiniciado).
- Anote la ronda en la tabla de la sección 6.

Qué se considera normal:
- **Reservas en conflicto**: dos asesores intentando reservar el mismo lote. Uno gana y el otro
  recibe un 4xx; k6 lo cuenta aparte y no lo suma a los errores.
- **Cambios de etapa rechazados**: una regla de negocio que no deja avanzar. Tampoco cuenta como
  error.
- El inicio de sesión es la petición más lenta (bcrypt). Su límite es 2 s.

## 4. Calendario (6 al 9 de noviembre)

| Día | Rondas | Resultado esperado |
| --- | --- | --- |
| 6 nov | `humo`, `carga` (línea base) | Cumple o no los criterios con 100 usuarios; lista de las peticiones más lentas |
| 7 nov | `estres`; ajustes (sección 5) y nueva `carga` | Punto de quiebre y su causa; primera mejora medida |
| 8 nov | `pico`; `carga` de confirmación | Se recupera solo del pico; criterios cumplidos con los ajustes |
| 9 nov | `resistencia` (1 h) | Memoria y errores estables. **Decisión de salida a producción** |

Entre rondas, espere a que la CPU baje y la cola `crm_pendientes` vuelva a 0.

## 5. Qué mirar y qué ajustar

Los ajustes se hacen en `/opt/nexora/.env` y se aplican con `docker compose up -d` (solo reinicia
lo que cambió). **Cambie una cosa por ronda** y anótela.

| Síntoma en Grafana | Causa probable | Ajuste |
| --- | --- | --- |
| p95 alto y "Pool de MySQL (Hikari)" con conexiones pendientes | Pocas conexiones a MySQL | `DB_POOL_MAX_SIZE=40` o `50`, sin pasar de `MYSQL_MAX_CONNECTIONS` (200) |
| "Hilos de Tomcat" ocupados = máximo | La API no da abasto con 200 hilos | Revisar primero las bases; si están sanas, más CPU (instancia más grande) |
| "Pool de MongoDB" con esperas | Pocas conexiones a MongoDB | `MONGO_POOL_MAX=150` |
| Memoria de la JVM cerca del máximo, pausas de GC | Poca memoria para la API | `API_MEMORIA=5g` |
| CPU de MySQL alta y lecturas lentas en una petición | Consulta sin índice | Ver la petición en la tabla de k6 y su consulta; índice nuevo en una migración |
| Atraso de espejos que crece sin bajar | El Change Stream no da abasto | Revisar errores `nexora-sync` en el log de la API |
| `crm_pendientes` que no baja | Falla al aplicar cambios en MongoDB | `SELECT * FROM crm_pendientes WHERE procesado_en IS NULL` y el error en `ultimo_error` |
| CPU de la máquina > 80 % con todo lo demás sano | La máquina se quedó corta | `m7i.2xlarge` (8 vCPU, 32 GiB): detener, cambiar el tipo y arrancar; la Elastic IP se conserva |
| Muchos 429 | Límite de intentos de inicio de sesión | Contraseña equivocada en `CARGA_PASSWORD`: revisar el secret |

## 6. Registro de resultados

| Fecha | Escenario | Versión | Ajustes | Máx. usuarios | Peticiones/s | p95 lectura | p95 escritura | Errores | CPU máx. | ¿Cumple? |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | | | | |

## 7. Limitaciones conocidas

- La carga sale de un solo runner de GitHub (una sola IP). Alcanza para 300 usuarios virtuales.
- Las métricas de k6 no llegan a Grafana: la vista del cliente está en el resumen de k6 y la del
  servidor en Grafana. Se cruzan por la hora.
- Cartera y pagos tienen pocos datos: la semilla crea reservas, pero no contratos firmados ni pagos.
  Esas pantallas se prueban con datos casi vacíos.
- **Nunca** corra `semilla.js` en producción: crea 100 usuarios con una contraseña que conoce el
  equipo de pruebas. Para quitarlos de staging: consola de superadministración → usuarios →
  inactivar, o restaurar el respaldo del paso 2.

## 8. Desde un computador (opcional)

Con [k6 instalado](https://grafana.com/docs/k6/latest/set-up/install-k6/):

```bash
BASE_URL=https://staging.ejemplo.com CARGA_PASSWORD='...' VOLUMEN=completo \
  k6 run -e ESCENARIO=humo carga/carga.js
```

Mejor desde una máquina en la misma región de AWS que desde una red de oficina o una casa: el
ancho de banda y la latencia de la red propia distorsionan el resultado.
