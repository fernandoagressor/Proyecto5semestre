# Despliegue en AWS (staging y producción)

NEXORA CORE corre en **una EC2 con Docker Compose** (`compose.prod.yaml`): MySQL, MongoDB (replica
set `rs0`), Redis, la API, el frontend y Caddy, que es el único servicio con puertos públicos y
saca el certificado HTTPS solo. El plan completo y el cronograma están en el documento
"Plan de salida a producción — NEXORA CORE (11 nov)".

Tiempo estimado la primera vez: 1 a 2 horas.

## 1. Cuenta y presupuesto (una sola vez)

1. Entre a la consola de AWS con el usuario raíz solo para crear un **usuario IAM administrador
   con MFA**; de ahí en adelante use ese usuario.
2. **Billing → Budgets → Create budget**: presupuesto mensual de USD 100 con alerta por correo al
   80 % y al 100 %.
3. Trabaje siempre en la región **us-east-1 (N. Virginia)**.

## 2. Contraseñas nuevas (obligatorio)

El archivo `.env` con las contraseñas de desarrollo estuvo publicado en el repositorio, así que
**ninguna de esas contraseñas se usa en AWS**. Genere todas nuevas en su computador:

```bash
for v in DB_PASSWORD DB_ROOT_PASSWORD MYSQL_MIGRATOR_PASSWORD MONGO_ROOT_PASSWORD \
         MONGO_APP_PASSWORD MONGO_MIGRATOR_PASSWORD REDIS_PASSWORD \
         MYSQL_EXPORTER_PASSWORD GRAFANA_ADMIN_PASSWORD; do
  echo "$v=$(openssl rand -base64 36 | tr -d '/+=' | cut -c1-32)"
done
echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '/+=')"
```

Guárdelas en el gestor de contraseñas del equipo. Solo letras y números: así sirven también
dentro de la URL de MongoDB. La contraseña del primer administrador la elige el equipo
(mínimo 12 caracteres con mayúscula, minúscula, número y símbolo).

## 3. Servidor EC2

**EC2 → Launch instance**:

| Campo | Valor |
| --- | --- |
| Nombre | `nexora-staging` (o `nexora-produccion`) |
| AMI | Ubuntu Server 24.04 LTS (x86_64) |
| Tipo | `m7i.xlarge` (4 vCPU, 16 GiB). No use `t3`: bajo carga sostenida gastan créditos de CPU y bajan el rendimiento |
| Par de llaves | Nuevo, tipo ED25519; guarde el `.pem` |
| Disco | 60 GB `gp3` |
| Grupo de seguridad | SSH 22 solo desde **Mi IP**; HTTP 80 y HTTPS 443 desde `0.0.0.0/0` y `::/0` |

Después: **Elastic IPs → Allocate** y **Associate** a la instancia, para que la IP no cambie al
reiniciar.

## 4. Dominio

Cree un registro **A** que apunte el dominio (por ejemplo `staging.nexora.com.co`) a la Elastic IP.
Compruébelo antes de seguir:

```bash
dig +short staging.nexora.com.co   # debe responder la Elastic IP
```

Caddy necesita que el dominio ya apunte al servidor y que el puerto 80 esté abierto para sacar
el certificado.

## 5. Preparar el servidor

```bash
ssh -i nexora.pem ubuntu@<ELASTIC_IP>

sudo apt-get update && sudo apt-get upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
sudo apt-get install -y jq git
sudo snap install aws-cli --classic   # para subir los respaldos a S3 (sección 11)
exit   # vuelva a entrar para que el grupo docker aplique
```

## 6. Primer despliegue

El servidor necesita una copia del repositorio (los archivos de compose, Caddy, el monitoreo y los
scripts) y el `.env`; las imágenes las baja ya construidas de GitHub Container Registry.

```bash
ssh -i nexora.pem ubuntu@<ELASTIC_IP>
sudo mkdir -p /opt/nexora && sudo chown ubuntu:ubuntu /opt/nexora
git clone --branch develop https://github.com/fernandoagressor/Proyecto5semestre.git /opt/nexora
cd /opt/nexora
cp deploy/produccion.env.example .env
chmod 600 .env
nano .env    # DOMINIO, ACME_EMAIL, las contraseñas del paso 2 y BOOTSTRAP_ADMIN_ENABLED=true
```

La plantilla ya trae `COMPOSE_FILE=compose.prod.yaml:compose.monitoring.yaml`: la aplicación y el
monitoreo se levantan juntos con un simple `docker compose`.

```bash
bash deploy/desplegar.sh develop        # baja las imágenes de develop y levanta todo
```

Si las imágenes todavía no están publicadas o el paquete de GHCR es privado (sección 10), se pueden
construir en el servidor: `docker compose up -d --build --wait --wait-timeout 900`.

Todos los servicios deben quedar `healthy` (Caddy, `running`). Abra `https://<DOMINIO>`, inicie
sesión con el administrador del `.env` y cambie la contraseña cuando la aplicación lo pida.

Luego **apague la creación del administrador inicial**:

```bash
sed -i 's/^BOOTSTRAP_ADMIN_ENABLED=.*/BOOTSTRAP_ADMIN_ENABLED=false/' .env
docker compose up -d
```

## 7. Prueba de humo en el servidor

```bash
cd /opt/nexora
BASE_URL=https://<DOMINIO> ADMIN_USUARIO=SUPERADMIN \
ADMIN_PASSWORD='<contraseña actual>' COMPOSE=1 \
bash scripts/prueba-humo.sh
```

Debe terminar con "Prueba de humo superada". Crea un proyecto, un prospecto, una tarea y un
artículo con la marca `HUMO`/`humo`; en staging no estorban.

## 8. Operación diaria

| Para | Comando (en `/opt/nexora`) |
| --- | --- |
| Ver el estado | `docker compose ps` |
| Ver logs de la API | `docker compose logs -f --tail 200 api` |
| Desplegar una versión | Desde GitHub, workflow **Desplegar** (sección 10); a mano: `git checkout <tag o commit> && bash deploy/desplegar.sh <versión>` |
| Reiniciar la API | `docker compose restart api` |
| Volver a una versión anterior | Desplegar la versión anterior ya publicada en GHCR (`sha-<commit>` o `X.Y.Z`); hasta la 0.3.1 no hay imágenes publicadas |
| Respaldar ahora | `bash deploy/respaldar.sh` |
| Ver los últimos respaldos | `journalctl -t nexora-respaldo -n 30` y `ls /var/lib/nexora/respaldos` |

`deploy/desplegar.sh` saca un respaldo antes de desplegar y vuelve solo a la versión anterior si
la nueva no queda sana en 10 minutos. Las migraciones de Flyway que la versión nueva alcanzó a
aplicar no se deshacen: para eso está el respaldo previo (sección 11).

## 9. Monitoreo (Grafana)

Prometheus y Grafana no son públicos: escuchan solo en `127.0.0.1` del servidor. Se abren desde
su computador con un túnel SSH:

```bash
ssh -i nexora.pem -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 ubuntu@<ELASTIC_IP>
```

Con el túnel abierto, entre a `http://localhost:3000` (usuario `admin`, contraseña
`GRAFANA_ADMIN_PASSWORD`). El tablero **NEXORA – Producción y pruebas de carga** abre solo y
muestra arriba los criterios de las pruebas de estrés: solicitudes por segundo, p95 de la API,
errores 5xx, CPU, atraso de las tablas espejo y cola `crm_pendientes`. Debajo están el detalle por
endpoint, los pools de MySQL y MongoDB, Redis, la JVM y la CPU y memoria de cada contenedor.

Prometheus guarda 15 días. Si MySQL ya existía antes de agregar el monitoreo, el usuario del
exportador no se creó solo; créelo una vez:

```bash
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
  CREATE USER IF NOT EXISTS '\''exporter'\''@'\''%'\'' IDENTIFIED BY '\''$MYSQL_EXPORTER_PASSWORD'\'' WITH MAX_USER_CONNECTIONS 3;
  GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO '\''exporter'\''@'\''%'\'';"'
```

## 10. Despliegue desde GitHub

Cada merge a `develop` publica las imágenes `ghcr.io/fernandoagressor/nexora-api` y
`nexora-frontend` con las etiquetas `develop` y `sha-<commit>`; cada tag `vX.Y.Z` publica `X.Y.Z` y
`latest` (workflow **Publicar imágenes**). El workflow **Desplegar** las instala en el servidor por
SSH.

**Una sola vez:**

1. **Paquetes públicos.** Después de la primera publicación, en GitHub entre a su perfil →
   **Packages** → `nexora-api` → **Package settings** → **Change visibility** → **Public**; lo
   mismo con `nexora-frontend`. El código ya es público; así el servidor baja las imágenes sin
   credenciales.
2. **Llave de despliegue** (en su computador):

   ```bash
   ssh-keygen -t ed25519 -f nexora-despliegue -N "" -C "github-actions"
   ssh-copy-id -i nexora-despliegue.pub -o IdentityFile=nexora.pem ubuntu@<ELASTIC_IP>
   ssh-keyscan -H <ELASTIC_IP>      # copie la salida para SSH_KNOWN_HOSTS
   ```

3. **Entornos en GitHub** (Settings → Environments → New environment): `staging` y `produccion`,
   cada uno con:

   | Tipo | Nombre | Valor |
   | --- | --- | --- |
   | Secret | `SSH_HOST` | Elastic IP del servidor de ese entorno |
   | Secret | `SSH_USER` | `ubuntu` |
   | Secret | `SSH_KEY` | Contenido de `nexora-despliegue` (la llave privada) |
   | Secret | `SSH_KNOWN_HOSTS` | Salida de `ssh-keyscan -H <ELASTIC_IP>` |
   | Secret | `ADMIN_USUARIO` / `ADMIN_PASSWORD` | Administrador para la prueba de humo de staging |
   | Secret | `CARGA_PASSWORD` | Solo en `staging`: usuarios de las pruebas de carga (`docs/PRUEBAS_DE_CARGA.md`) |
   | Variable | `DOMINIO` | `staging.nexora.com.co` (o el de producción) |

   En `produccion` active **Required reviewers** con Fernando: nadie despliega a producción sin
   aprobarlo.
4. Para que cada merge a `develop` se despliegue solo en staging: Settings → Secrets and variables
   → Actions → **Variables** → `STAGING_AUTODEPLOY` = `true`.

**Desplegar a mano:** Actions → **Desplegar** → Run workflow → entorno y versión (`develop`,
`sha-<commit>` o `0.4.0`). Staging corre además la prueba de humo completa; producción solo
comprueba la salud por HTTPS, para no crear datos de prueba.

## 11. Respaldos

Cada día a las 03:15 (hora de Colombia) `deploy/respaldar.sh` respalda MySQL, MongoDB y los
documentos subidos, los sube a S3 y conserva 3 días en el servidor. Cada domingo
`deploy/probar-restauracion.sh` carga el último respaldo en un MySQL y un MongoDB temporales y
comprueba que esté completo, sin tocar los datos reales. Redis no se respalda (solo es caché).

**Una sola vez (consola de AWS, región us-east-1):**

1. **S3 → Create bucket**: nombre `nexora-respaldos-<entorno>-<algo único>` (p. ej.
   `nexora-respaldos-staging-altamar`). Deje **Block all public access** marcado y el cifrado por
   defecto (SSE-S3).
2. En el bucket: **Management → Create lifecycle rule**, prefijo `respaldos/`, **Expire current
   versions of objects** a los 30 días (90 en producción si el equipo lo prefiere).
3. **IAM → Policies → Create policy → JSON** (cambie `<BUCKET>`), nombre `nexora-respaldos`:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Effect": "Allow", "Action": ["s3:PutObject", "s3:GetObject"],
         "Resource": "arn:aws:s3:::<BUCKET>/respaldos/*" },
       { "Effect": "Allow", "Action": "s3:ListBucket", "Resource": "arn:aws:s3:::<BUCKET>",
         "Condition": { "StringLike": { "s3:prefix": ["respaldos/*"] } } }
     ]
   }
   ```

   El servidor puede subir y bajar respaldos pero **no borrarlos**: si alguien entra al servidor
   no puede eliminar las copias de S3. Las borra solo la regla del paso 2.
4. **IAM → Roles → Create role** → *AWS service* → *EC2* → política `nexora-respaldos`, nombre
   `nexora-ec2`. Luego en **EC2 → la instancia → Actions → Security → Modify IAM role** →
   `nexora-ec2`. Así el servidor no guarda llaves de AWS.
5. En el servidor:

   ```bash
   cd /opt/nexora
   echo 'RESPALDO_S3_BUCKET=<BUCKET>' >> .env    # o edítelo con nano si ya existe la línea
   bash deploy/instalar-respaldos.sh           # carpetas, prueba de acceso a S3 y cron
   bash deploy/respaldar.sh && bash deploy/probar-restauracion.sh
   ```

   Debe terminar con "Prueba de restauración superada". En Grafana, la fila **Respaldos** muestra
   cuánto hace del último respaldo y de la última prueba (rojo si pasan más de 26 horas u 8 días).

**Restaurar** (reemplaza todos los datos del servidor; antes saca un respaldo local de lo que hay):

```bash
cd /opt/nexora
aws s3 ls s3://<BUCKET>/respaldos/                 # elegir el respaldo
bash deploy/restaurar.sh s3://<BUCKET>/respaldos/nexora-AAAAMMDD-HHMMSS --confirmar
BASE_URL=https://<DOMINIO> ADMIN_USUARIO=SUPERADMIN ADMIN_PASSWORD='<contraseña>' COMPOSE=1 \
  bash scripts/prueba-humo.sh
```

- La versión desplegada debe ser la del respaldo o una posterior (`version=` en
  `MANIFIESTO.txt`). Para volver atrás una versión con migraciones: desplegar la versión
  anterior **y** restaurar el respaldo que `desplegar.sh` sacó justo antes.
- En un servidor nuevo (desastre): preparar el servidor (secciones 3 a 6) con las **mismas
  contraseñas** del `.env` anterior, `bash deploy/instalar-respaldos.sh` y restaurar con
  `--sin-respaldo-previo`.
- Las tablas espejo `lectura_*` se reconstruyen solas desde MongoDB al arrancar la API y la caché
  de Redis queda vacía.

Opcional y barato como segunda capa: **EC2 → Lifecycle Manager → Create policy** con snapshots
diarios del disco de la instancia, 7 días de retención.

## Seguridad: qué queda expuesto

- Solo los puertos 80 y 443 (Caddy) y 22 desde la IP del equipo. MySQL, MongoDB, Redis, la API y
  el frontend no publican puertos: solo se hablan dentro de la red de Docker.
- De `/actuator` solo responde `/actuator/health`; el resto devuelve 404. Las métricas
  (`/actuator/prometheus`) las lee Prometheus dentro de la red de Docker.
- Grafana y Prometheus solo en `127.0.0.1`, por túnel SSH.
- HTTPS con HSTS. La IP real de cada cliente llega a la API desde Caddy, así el límite de 20
  intentos de inicio de sesión fallidos por IP no se puede engañar.
- `.env` con permisos `600` solo en el servidor; nunca en el repositorio. Los respaldos no lo
  incluyen: las contraseñas viven en el gestor de contraseñas del equipo.
- Respaldos en un bucket privado y cifrado; el servidor no puede borrarlos (sección 11).
