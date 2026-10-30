# Funciones compartidas por respaldar.sh, restaurar.sh y probar-restauracion.sh (no se ejecuta solo).

# Valor de una variable: primero el entorno, después .env.
leer_env() {
  local nombre="$1" valor="${!1:-}"
  if [[ -z "$valor" && -f .env ]]; then
    valor="$(grep -E "^${nombre}=" .env | tail -1 | cut -d= -f2- || true)"
  fi
  printf '%s' "$valor"
}

RESPALDO_DIR="$(leer_env RESPALDO_DIR)"; RESPALDO_DIR="${RESPALDO_DIR:-/var/lib/nexora/respaldos}"
METRICAS_DIR="$(leer_env METRICAS_DIR)"; METRICAS_DIR="${METRICAS_DIR:-/var/lib/nexora/metricas}"
RESPALDO_S3_BUCKET="$(leer_env RESPALDO_S3_BUCKET)"
# RESPALDO_SOLO_LOCAL=1 no sube a S3 aunque haya bucket (respaldo previo a restaurar).
if [[ "${RESPALDO_SOLO_LOCAL:-0}" == 1 ]]; then RESPALDO_S3_BUCKET=""; fi

registro() { printf '%s %s\n' "$(date '+%F %T')" "$*"; }
error() {
  printf '%s ERROR %s\n' "$(date '+%F %T')" "$*" >&2
  [[ "${GITHUB_ACTIONS:-}" == true ]] && echo "::error title=Respaldos::$*"
  exit 1
}

# MANIFIESTO.txt es clave=valor.
manifiesto() { grep -E "^$2=" "$1/MANIFIESTO.txt" | cut -d= -f2- || true; }

# Escribe métricas para Prometheus (colector textfile de node-exporter). Sin la carpeta, no hace nada.
publicar_metricas() {
  local archivo="$1" contenido="$2"
  [[ -d "$METRICAS_DIR" && -w "$METRICAS_DIR" ]] || return 0
  local tmp; tmp="$(mktemp "$METRICAS_DIR/.$archivo.XXXXXX")"
  printf '%s\n' "$contenido" > "$tmp"
  chmod 644 "$tmp"
  mv "$tmp" "$METRICAS_DIR/$archivo"
}

# Comandos dentro de los contenedores, con las credenciales que ya tienen en su entorno
# (las contraseñas no pasan por la línea de comandos del servidor).
mysql_root() {
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$@"' mysql "$@"
}
mongo_root() {
  docker compose exec -T mongodb sh -c \
    'exec mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin "$MONGO_APP_DATABASE" --eval "$0"' \
    "$1" | tail -1
}

# Un respaldo: carpeta local, o s3://bucket/respaldos/nexora-... que se baja primero.
obtener_respaldo() {
  local origen="$1"
  if [[ "$origen" == s3://* ]]; then
    command -v aws > /dev/null || error "Falta la AWS CLI para bajar $origen"
    local local_dir; local_dir="$RESPALDO_DIR/descargado-$(basename "$origen")"
    rm -rf "$local_dir"; mkdir -p "$local_dir"
    registro "Bajando $origen" >&2
    aws s3 cp --recursive --only-show-errors "${origen%/}/" "$local_dir/" >&2 || error "No se pudo bajar $origen"
    printf '%s' "$local_dir"
  else
    [[ -d "$origen" ]] || error "No existe el respaldo $origen"
    printf '%s' "$(cd "$origen" && pwd)"
  fi
}

verificar_integridad() {
  local dir="$1"
  for f in mysql.sql.gz mongodb.archive documentos.tar.gz MANIFIESTO.txt SHA256SUMS; do
    [[ -s "$dir/$f" ]] || error "Respaldo incompleto: falta $f en $dir"
  done
  (cd "$dir" && sha256sum --quiet -c SHA256SUMS) || error "El sha256 de $dir no coincide: archivo dañado"
}

ultimo_respaldo_local() {
  find "$RESPALDO_DIR" -mindepth 1 -maxdepth 1 -type d -name 'nexora-*' ! -name '*.parcial' | sort | tail -1
}
