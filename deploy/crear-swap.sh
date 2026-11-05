#!/usr/bin/env bash
# Crea un archivo de swap en el servidor (por defecto 2 GiB) y lo deja activo después de cada
# reinicio. En un servidor de 4 GiB evita que un pico (respaldo, despliegue, reporte grande)
# termine en un contenedor cerrado por falta de memoria. Se puede correr varias veces.
#
#   sudo bash deploy/crear-swap.sh        # 2 GiB
#   sudo bash deploy/crear-swap.sh 4      # 4 GiB
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Ejecute con sudo" >&2; exit 1; }
gib="${1:-2}"
[[ "$gib" =~ ^[1-8]$ ]] || { echo "Tamaño en GiB entre 1 y 8" >&2; exit 1; }
archivo=/swapfile

if swapon --show=NAME --noheadings | grep -qx "$archivo"; then
  echo "El swap ya está activo:"
else
  if [[ ! -f "$archivo" ]]; then
    fallocate -l "${gib}G" "$archivo" || dd if=/dev/zero of="$archivo" bs=1M count=$((gib * 1024)) status=none
  fi
  chmod 600 "$archivo"
  mkswap "$archivo" > /dev/null
  swapon "$archivo"
  echo "Swap de ${gib} GiB activado:"
fi
grep -q "^$archivo " /etc/fstab || echo "$archivo none swap sw 0 0" >> /etc/fstab

# Usar el swap solo cuando de verdad falte memoria (las bases rinden mejor en RAM).
cat > /etc/sysctl.d/90-nexora-swap.conf <<'CONF'
vm.swappiness=10
vm.vfs_cache_pressure=50
CONF
sysctl -q --system
swapon --show
free -h
