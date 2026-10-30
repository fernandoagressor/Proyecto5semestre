#!/usr/bin/env bash
# Prepara los respaldos automáticos en el servidor (una sola vez; se puede repetir):
#
#   cd /opt/nexora && bash deploy/instalar-respaldos.sh
#
# Crea las carpetas de respaldos y métricas, comprueba la AWS CLI y el acceso al bucket, y
# programa en el cron del usuario:
#   - el respaldo diario a las 03:15 hora de Colombia (08:15 UTC);
#   - la prueba de restauración los domingos a las 04:45 hora de Colombia (09:45 UTC).
# Los registros quedan en el journal: journalctl -t nexora-respaldo -t nexora-restauracion

set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/respaldos-comun.sh
raiz="$(pwd)"

for d in "$RESPALDO_DIR" "$METRICAS_DIR"; do
  sudo mkdir -p "$d"
  sudo chown "$(id -un):$(id -gn)" "$d"
done
chmod 700 "$RESPALDO_DIR"
chmod 755 "$METRICAS_DIR"
registro "Carpetas: $RESPALDO_DIR (respaldos) y $METRICAS_DIR (métricas)"

if [[ -n "$RESPALDO_S3_BUCKET" ]]; then
  command -v aws > /dev/null || error "Instale la AWS CLI: sudo snap install aws-cli --classic"
  prueba="s3://$RESPALDO_S3_BUCKET/respaldos/.prueba-acceso-$(hostname)"
  echo "ok" | aws s3 cp --only-show-errors - "$prueba" \
    || error "No se puede escribir en s3://$RESPALDO_S3_BUCKET/respaldos/ (¿rol IAM de la instancia?)"
  registro "Acceso a s3://$RESPALDO_S3_BUCKET/respaldos/ comprobado"
else
  registro "AVISO: sin RESPALDO_S3_BUCKET en .env los respaldos quedan solo en este servidor"
fi

command -v crontab > /dev/null || error "Falta cron: sudo apt-get install -y cron"
marca="# nexora-respaldos"
nuevas="PATH=/snap/bin:/usr/local/bin:/usr/bin:/bin $marca
15 8 * * * cd $raiz && bash deploy/respaldar.sh 2>&1 | logger -t nexora-respaldo $marca
45 9 * * 0 cd $raiz && bash deploy/probar-restauracion.sh 2>&1 | logger -t nexora-restauracion $marca"
{ crontab -l 2>/dev/null | grep -v "$marca" || true; echo "$nuevas"; } | crontab -
registro "Cron instalado:"
crontab -l | grep "$marca"
registro "Primer respaldo ahora: bash deploy/respaldar.sh && bash deploy/probar-restauracion.sh"
