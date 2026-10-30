#!/usr/bin/env bash
# Prueba de humo de punta a punta de NEXORA CORE.
#
# Recorre la aplicación como un usuario: frontend, inicio de sesión, dashboards, un proyecto
# (MySQL), un prospecto y una tarea (MongoDB), la sincronización a las tablas espejo, la
# auditoría después del commit y la base de conocimiento. Con COMPOSE=1 revisa además las
# bases por dentro (colecciones, índices, espejos, cola crm_pendientes) con docker compose.
#
# Uso:
#   BASE_URL=http://localhost:4200 ADMIN_USUARIO=SUPERADMIN ADMIN_PASSWORD='...' \
#   ADMIN_PASSWORD_NUEVA='...' COMPOSE=1 bash scripts/prueba-humo.sh
#
# Sirve igual contra staging (sin COMPOSE): BASE_URL=https://staging.ejemplo.com ...
# Necesita curl y jq. Termina con código 1 si algún paso falla.

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:4200}"
ADMIN_USUARIO="${ADMIN_USUARIO:-SUPERADMIN}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD}"
ADMIN_PASSWORD_NUEVA="${ADMIN_PASSWORD_NUEVA:-$ADMIN_PASSWORD}"
COMPOSE="${COMPOSE:-0}"
# CURL_INSECURE=1 acepta certificados no públicos (Caddy con DOMINIO=localhost en el CI).
CURL_EXTRA=()
[[ "${CURL_INSECURE:-0}" == 1 ]] && CURL_EXTRA=(--insecure)
SELLO="$(date +%s)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

ok=0
fallos=0
TOKEN=""

verde() { printf '\033[32m%s\033[0m\n' "$*"; }
rojo() { printf '\033[31m%s\033[0m\n' "$*"; }

paso() { ok=$((ok + 1)); verde "  ✔ $*"; }
falla() {
  fallos=$((fallos + 1)); rojo "  ✘ $*"
  # En GitHub Actions cada falla queda también como anotación del PR (se lee sin bajar el log).
  if [[ "${GITHUB_ACTIONS:-}" == true ]]; then
    local msg="${*//$'\n'/ }"; echo "::error title=Prueba de humo::${msg:0:900}"
  fi
}

# api MÉTODO RUTA [JSON] → deja el código en $STATUS y el cuerpo en $TMP/cuerpo
api() {
  local metodo="$1" ruta="$2" datos="${3:-}"
  local args=(-s "${CURL_EXTRA[@]}" -o "$TMP/cuerpo" -w '%{http_code}' -X "$metodo" "$BASE_URL$ruta"
              -H 'Accept: application/json')
  [[ -n "$TOKEN" ]] && args+=(-H "Authorization: Bearer $TOKEN")
  [[ -n "$datos" ]] && args+=(-H 'Content-Type: application/json' --data "$datos")
  STATUS="$(curl "${args[@]}")"
}

cuerpo() { jq -r "$1" "$TMP/cuerpo" 2>/dev/null; }

esperar() { # esperar SEGUNDOS comando... → reintenta cada segundo hasta que el comando salga bien
  local limite="$1"; shift
  for _ in $(seq "$limite"); do
    if "$@"; then return 0; fi
    sleep 1
  done
  return 1
}

mysql_q() {
  docker compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -s -e "$0"' "$1" 2>/dev/null
}

mongo_q() {
  docker compose exec -T mongodb sh -c \
    'mongosh --quiet -u "$MONGO_APP_USER" -p "$MONGO_APP_PASSWORD" --authenticationDatabase "$MONGO_APP_DATABASE" "$MONGO_APP_DATABASE" --eval "$0"' \
    "$1" 2>/dev/null | tail -1
}

echo "Prueba de humo contra $BASE_URL"

echo "1. Frontend y salud"
codigo="$(curl -s "${CURL_EXTRA[@]}" -o "$TMP/index" -w '%{http_code}' "$BASE_URL/")"
if [[ "$codigo" == 200 ]] && grep -q '<app-root' "$TMP/index"; then paso "El frontend responde con la aplicación Angular"
else falla "El frontend respondió $codigo sin <app-root>"; fi

codigo="$(curl -s "${CURL_EXTRA[@]}" -o /dev/null -w '%{http_code}' "$BASE_URL/healthz")"
[[ "$codigo" == 200 ]] && paso "nginx /healthz" || falla "nginx /healthz respondió $codigo"

codigo="$(curl -s "${CURL_EXTRA[@]}" -o "$TMP/salud" -w '%{http_code}' "$BASE_URL/actuator/health/readiness")"
if [[ "$codigo" == 200 ]] && grep -q '"UP"' "$TMP/salud"; then paso "Readiness UP (MySQL, MongoDB y Redis)"
elif [[ "$codigo" == 404 || "$codigo" == 403 ]]; then paso "Actuator cerrado al público ($codigo)"
else falla "Readiness respondió $codigo: $(cat "$TMP/salud")"; fi

echo "2. Inicio de sesión"
iniciar() {
  local clave="$1"
  api POST /api/v1/auth/login "$(jq -n --arg u "$ADMIN_USUARIO" --arg p "$clave" '{identificador:$u, password:$p}')"
  [[ "$STATUS" == 200 ]] && TOKEN="$(cuerpo .accessToken)"
}
TOKEN=""
if iniciar "$ADMIN_PASSWORD" || { TOKEN=""; iniciar "$ADMIN_PASSWORD_NUEVA"; }; then
  paso "Inicio de sesión de $ADMIN_USUARIO"
else
  falla "No se pudo iniciar sesión (HTTP $STATUS: $(cat "$TMP/cuerpo"))"
  echo; rojo "Sin sesión no se puede seguir: $ok bien, $fallos con error"; exit 1
fi

if [[ "$(cuerpo .usuario.requiereCambioPassword)" == true ]]; then
  api POST /api/v1/auth/cambiar-password "$(jq -n --arg a "$ADMIN_PASSWORD" --arg n "$ADMIN_PASSWORD_NUEVA" \
    '{passwordActual:$a, passwordNueva:$n, confirmacionPassword:$n}')"
  if [[ "$STATUS" =~ ^20 ]]; then
    TOKEN=""
    iniciar "$ADMIN_PASSWORD_NUEVA" && paso "Cambio de contraseña obligatorio y nuevo inicio de sesión" \
      || falla "No se pudo iniciar sesión con la contraseña nueva (HTTP $STATUS)"
  else
    falla "Cambio de contraseña respondió $STATUS: $(cat "$TMP/cuerpo")"
  fi
fi
USUARIO_ID="$(cuerpo .usuario.id)"

api GET /api/v1/empresas/actual
[[ "$STATUS" == 200 ]] && paso "Empresa actual: $(cuerpo .nombre)" || falla "Empresa actual respondió $STATUS"

echo "3. Dashboards (MySQL, tablas espejo, Redis)"
for tablero in gerencial comercial cartera servicio; do
  api GET "/api/v1/dashboards/$tablero"
  [[ "$STATUS" == 200 ]] && paso "Dashboard $tablero" || falla "Dashboard $tablero respondió $STATUS: $(cat "$TMP/cuerpo")"
done

echo "4. Proyecto (MySQL)"
api POST /api/v1/proyectos "$(jq -n --arg c "HUMO-$SELLO" '{codigo:$c, nombre:"Proyecto de humo", ciudad:"Cartagena", departamento:"Bolívar", maxLotes:10}')"
PROYECTO_ID="$(cuerpo .id)"
[[ "$STATUS" == 201 && "$PROYECTO_ID" != null ]] && paso "Proyecto creado ($PROYECTO_ID)" \
  || falla "Crear proyecto respondió $STATUS: $(cat "$TMP/cuerpo")"

echo "5. Prospecto (MongoDB)"
api POST /api/v1/prospectos "$(jq -n --argjson p "${PROYECTO_ID:-null}" --arg s "$SELLO" \
  '{proyectoId:$p, nombres:("Prospecto humo " + $s), telefono:"3000000000", correo:("humo" + $s + "@ejemplo.com"), fuente:"WEB", temperatura:"TIBIO", presupuesto:150000000}')"
PROSPECTO_ID="$(cuerpo .id)"
[[ "$STATUS" == 201 && "$PROSPECTO_ID" != null ]] && paso "Prospecto creado ($PROSPECTO_ID)" \
  || falla "Crear prospecto respondió $STATUS: $(cat "$TMP/cuerpo")"
api GET "/api/v1/prospectos?texto=humo%20$SELLO"
[[ "$STATUS" == 200 && "$(cuerpo "any(.contenido[]; .id == ${PROSPECTO_ID:-0})")" == true ]] \
  && paso "El prospecto aparece en el listado" || falla "El listado de prospectos no lo trae (HTTP $STATUS)"

echo "6. Tarea (MongoDB), espejo y auditoría"
VENCE="$(date -u -d '+3 days' +%Y-%m-%dT10:00:00)"
api POST /api/v1/tareas "$(jq -n --argjson p "${PROYECTO_ID:-null}" --argjson u "${USUARIO_ID:-null}" --arg v "$VENCE" \
  '{proyectoId:$p, asignadoA:$u, titulo:"Llamar al prospecto de humo", tipo:"seguimiento", prioridad:"ALTA", fechaVencimiento:$v}')"
TAREA_ID="$(cuerpo .id)"
[[ "$STATUS" == 201 && "$TAREA_ID" != null ]] && paso "Tarea creada ($TAREA_ID)" \
  || falla "Crear tarea respondió $STATUS: $(cat "$TMP/cuerpo")"
api PATCH "/api/v1/tareas/$TAREA_ID/estado" '{"estado":"EN_PROCESO","comentario":"Prueba de humo"}'
[[ "$STATUS" == 200 && "$(cuerpo .estado)" == EN_PROCESO ]] && paso "Tarea en proceso (control de versión OK)" \
  || falla "Cambiar estado respondió $STATUS: $(cat "$TMP/cuerpo")"
api GET "/api/v1/tareas/$TAREA_ID/historial"
[[ "$STATUS" == 200 && "$(cuerpo 'length')" -ge 2 ]] && paso "Historial embebido con $(cuerpo 'length') registros" \
  || falla "Historial de la tarea respondió $STATUS"

auditada() { api GET "/api/v1/auditoria/eventos?accion=TAREA_ESTADO&tamano=50"; [[ "$STATUS" == 200 && "$(cuerpo '.totalElementos')" -ge 1 ]]; }
esperar 15 auditada && paso "Evento de auditoría escrito después del commit" || falla "No apareció el evento TAREA_ESTADO (HTTP $STATUS)"

if [[ "$COMPOSE" == 1 ]]; then
  espejo() { [[ "$(mysql_q "SELECT estado FROM lectura_tareas WHERE id = $TAREA_ID")" == EN_PROCESO ]]; }
  esperar 30 espejo && paso "Change Stream proyectó la tarea a lectura_tareas" \
    || falla "lectura_tareas no tiene la tarea $TAREA_ID en EN_PROCESO"
fi

echo "7. Base de conocimiento (MongoDB, contenido de empresa)"
api POST /api/v1/base-conocimiento/categorias "$(jq -n --arg n "Humo $SELLO" '{nombre:$n, orden:1}')"
CATEGORIA_ID="$(cuerpo .id)"
[[ "$STATUS" =~ ^20 && "$CATEGORIA_ID" != null ]] && paso "Categoría creada" || falla "Crear categoría respondió $STATUS: $(cat "$TMP/cuerpo")"
api POST /api/v1/base-conocimiento/articulos "$(jq -n --argjson c "${CATEGORIA_ID:-0}" --arg s "$SELLO" \
  '{categoriaId:$c, titulo:("Cómo (pagar) humo " + $s), contenido:"Texto de prueba", visibleClientes:true}')"
ARTICULO_ID="$(cuerpo .id)"
[[ "$STATUS" =~ ^20 && "$ARTICULO_ID" != null ]] && paso "Artículo creado" || falla "Crear artículo respondió $STATUS: $(cat "$TMP/cuerpo")"
api PATCH "/api/v1/base-conocimiento/articulos/$ARTICULO_ID/publicar"
[[ "$STATUS" == 200 && "$(cuerpo .estado)" == PUBLICADO ]] && paso "Artículo publicado" || falla "Publicar respondió $STATUS"
api GET "/api/v1/base-conocimiento/articulos?texto=(pagar)%20humo%20$SELLO"
[[ "$STATUS" == 200 && "$(cuerpo '.totalElementos')" -ge 1 ]] && paso "Búsqueda con paréntesis encuentra el artículo" \
  || falla "La búsqueda no lo encontró (HTTP $STATUS)"

echo "8. Notificaciones"
api GET /api/v1/notificaciones/resumen
[[ "$STATUS" == 200 ]] && paso "Resumen de notificaciones" || falla "Notificaciones respondió $STATUS"

if [[ "$COMPOSE" == 1 ]]; then
  echo "9. Bases por dentro"
  colecciones="$(mongo_q 'db.getCollectionNames().sort().join(",")')"
  for c in prospectos tareas auditoria_eventos articulos_conocimiento contadores sync_tokens; do
    [[ ",$colecciones," == *",$c,"* ]] && paso "Colección $c" || falla "Falta la colección $c (hay: $colecciones)"
  done
  unico="$(mongo_q 'db.estados_cuenta.getIndexes().some(i => i.unique === true) ? "si" : "no"')"
  [[ "$unico" == si ]] && paso "Índice único de estados_cuenta creado al arrancar" || falla "estados_cuenta no tiene índice único"
  ttl="$(mongo_q 'db.intentos_acceso.getIndexes().some(i => i.expireAfterSeconds !== undefined) ? "si" : "no"')"
  [[ "$ttl" == si ]] && paso "Índice TTL de intentos_acceso" || falla "intentos_acceso no tiene índice TTL"
  pendientes="$(mysql_q "SELECT COUNT(*) FROM crm_pendientes WHERE procesado_en IS NULL")"
  [[ "$pendientes" == 0 ]] && paso "crm_pendientes sin pendientes" || falla "crm_pendientes tiene $pendientes pendientes"
  version="$(mysql_q "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1")"
  [[ "$version" == 29 ]] && paso "Flyway en V29" || falla "Flyway quedó en V$version"
  errores="$(docker compose logs --no-color api 2>/dev/null | grep -c ' ERROR ' || true)"
  if [[ "$errores" == 0 ]]; then paso "Sin líneas ERROR en el log de la API"
  else
    falla "$errores líneas ERROR en el log de la API"
    docker compose logs --no-color api 2>/dev/null | grep ' ERROR ' | head -5 | while IFS= read -r linea; do
      [[ "${GITHUB_ACTIONS:-}" == true ]] && echo "::warning title=ERROR en la API::${linea:0:900}"
    done
  fi
fi

echo
if [[ "$fallos" == 0 ]]; then verde "Prueba de humo superada: $ok pasos bien"; exit 0
else rojo "Prueba de humo con errores: $ok bien, $fallos con error"; exit 1; fi
