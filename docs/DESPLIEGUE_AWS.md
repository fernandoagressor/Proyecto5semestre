# Despliegue en AWS (staging y producción)

NEXORA CORE corre en **una EC2 con Docker Compose** (`compose.prod.yaml`): MySQL, MongoDB (replica
set `rs0`), Redis, la API, el frontend y Caddy, que es el único servicio con puertos públicos y
saca el certificado HTTPS solo. El plan completo y el cronograma están en el documento
"Plan de salida a producción — NEXORA CORE (11 nov)".

Tiempo estimado la primera vez: 1 a 2 horas.

## 1. Cuenta, proyecto y límite de gasto (una sola vez por entorno)

Las cuentas nuevas de AWS (desde septiembre de 2026) usan la **nueva experiencia** en
[settings.aws.com](https://settings.aws.com): cada **proyecto** es una cuenta de AWS aparte, con su
propia factura y su propio límite de gasto.

1. **Plan pago.** Configuración → **Facturación** → **Actualizar**. El plan gratuito no permite
   máquinas xlarge. Los créditos se siguen usando primero.
2. **Un proyecto por entorno.** Configuración → **Proyectos** → **Crear proyecto**:
   `nexora.staging` y, para la entrega, `nexora.produccion` (solo letras, números y puntos). Así un
   error en staging no toca producción.
3. **Límite de gasto** (antes de crear nada). Facturación → **Costo por proyecto** → lápiz en
   **Límite de gasto**:
   - `nexora.staging`: USD 100. Como control anticipado, solo *Stop new resource creation*.
   - `nexora.produccion`: con margen (USD 250 o más) y **sin** controles de pausa.
   - Al llegar al límite **AWS pausa el proyecto y apaga todo**: la aplicación se cae. Avisa al 50 %,
     75 % y 90 %.
4. **Región**: la asigna AWS según el país de la cuenta. Desde Colombia es **US East (Ohio)
   us-east-2**; se ve en el proyecto → *Información adicional* → **Región**. La consola del proyecto
   no tiene selector de región.
5. Para trabajar, abra el proyecto y su consola (la URL empieza por `<cuenta>-<id>.us-east-2.console…`).

En una cuenta con la consola clásica: usuario IAM administrador con MFA, **Billing → Budgets** en
vez del límite de gasto y la región que el equipo elija.

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
| Grupo de seguridad | `nexora-staging-sg`: SSH 22 solo desde **Mi IP**; HTTP 80 y HTTPS 443 desde **Anywhere-IPv4** y **Anywhere-IPv6** (5 reglas) |
| Detalles avanzados → Perfil de instancia de IAM | `nexora-ec2` (sección 11; créelo antes de lanzar) |

Después: **Direcciones IP elásticas → Asignar** y **Asociar** a la instancia, para que la IP no
cambie al apagar o reiniciar.

La llave `.pem` en Windows: guárdela en `C:\Users\<usted>\.ssh\` y déjela legible solo por usted
(si no, `ssh` se niega a usarla):

```powershell
icacls "$HOME\.ssh\nexora-staging.pem" /inheritance:r
icacls "$HOME\.ssh\nexora-staging.pem" /grant:r "$($env:USERNAME):(R)"
```

## 4. Dominio

Cree un registro **A** que apunte el dominio (por ejemplo `staging.nexora.com.co`) a la Elastic IP.
Compruébelo antes de seguir:

```bash
dig +short staging.nexora.com.co   # debe responder la Elastic IP
```

Caddy necesita que el dominio ya apunte al servidor y que el puerto 80 esté abierto para sacar
el certificado.

**Sin dominio propio (staging):** use `<ip-con-guiones>.sslip.io`. Por ejemplo, para la IP
`13.58.119.98`, `DOMINIO=13-58-119-98.sslip.io`: ese nombre ya apunta a la IP y Caddy le saca el
certificado igual. Al comprar el dominio solo cambia esa línea del `.env`.

## 5. Preparar el servidor

Desde Windows, si PowerShell responde que `ssh` no se reconoce, instale el cliente una vez
(PowerShell **como administrador**) y abra otra ventana:

```powershell
Add-WindowsCapability -Online -Name OpenSSH.Client~~~~0.0.1.0
ssh -i "$HOME\.ssh\nexora-staging.pem" ubuntu@<ELASTIC_IP>
```

En el servidor:

```bash
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a apt-get upgrade -y
sudo apt-get install -y jq git cron
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
sudo snap install aws-cli --classic   # para subir los respaldos a S3 (sección 11)
sudo reboot                           # vuelva a entrar en un minuto
```

Al volver, compruebe: `docker compose version` (v2.30 o más), `aws sts get-caller-identity` (debe
decir `assumed-role/nexora-ec2`). No acepte el aviso de pasar a otra versión de Ubuntu.

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
gen() { openssl rand -base64 36 | tr -d '/+=' | cut -c1-32; }
poner() { sed -i "s|^$1=.*|$1=$2|" .env; }
for v in DB_PASSWORD DB_ROOT_PASSWORD MYSQL_MIGRATOR_PASSWORD MONGO_ROOT_PASSWORD MONGO_APP_PASSWORD \
         MONGO_MIGRATOR_PASSWORD REDIS_PASSWORD MYSQL_EXPORTER_PASSWORD GRAFANA_ADMIN_PASSWORD; do
  poner "$v" "$(gen)"
done
poner JWT_SECRET "$(openssl rand -base64 48 | tr -d '/+=')"
poner DOMINIO <dominio>
poner RESPALDO_S3_BUCKET <bucket>
poner BOOTSTRAP_ADMIN_ENABLED true
poner BOOTSTRAP_ADMIN_PASSWORD "Nx-$(gen)!"
poner ACME_EMAIL '<correo del equipo>'
poner BOOTSTRAP_EMPRESA_NIT '<NIT>'
poner BOOTSTRAP_ADMIN_DOCUMENTO '<documento>'
poner BOOTSTRAP_ADMIN_EMAIL '<correo del administrador>'
cat .env     # cópielo al gestor de contraseñas del equipo; nunca al chat ni al repositorio
```

Así las contraseñas se generan en el servidor (el paso 2 queda como alternativa).

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

**Ojo con el puerto 22.** Los runners de GitHub salen de IP que cambian, y el grupo de seguridad
solo deja entrar por SSH desde la IP del equipo: así el workflow **Desplegar** no puede conectarse.
Mientras tanto se despliega a mano por SSH (`cd /opt/nexora && git pull && bash
deploy/desplegar.sh develop`). Abrir el 22 a todo internet funciona (solo se entra con llave), pero
expone el servidor a intentos constantes; para producción conviene decidirlo aparte. El workflow
**Prueba de carga** no usa SSH: solo necesita `DOMINIO`, `ADMIN_USUARIO`, `ADMIN_PASSWORD` y
`CARGA_PASSWORD` en el entorno `staging`.

Desde Windows, la llave de despliegue se instala en el servidor así (no existe `ssh-copy-id`):

```powershell
ssh-keygen -t ed25519 -f "$HOME\.ssh\nexora-despliegue" -N '""' -C github-actions
type "$HOME\.ssh\nexora-despliegue.pub" | ssh -i "$HOME\.ssh\nexora-staging.pem" ubuntu@<ELASTIC_IP> "cat >> ~/.ssh/authorized_keys"
ssh-keyscan -H <ELASTIC_IP>
```

**Desplegar a mano:** Actions → **Desplegar** → Run workflow → entorno y versión (`develop`,
`sha-<commit>` o `0.4.0`). Staging corre además la prueba de humo completa; producción solo
comprueba la salud por HTTPS, para no crear datos de prueba.

## 11. Respaldos

Cada día a las 03:15 (hora de Colombia) `deploy/respaldar.sh` respalda MySQL, MongoDB y los
documentos subidos, los sube a S3 y conserva 3 días en el servidor. Cada domingo
`deploy/probar-restauracion.sh` carga el último respaldo en un MySQL y un MongoDB temporales y
comprueba que esté completo, sin tocar los datos reales. Redis no se respalda (solo es caché).

**Una sola vez (en la consola del proyecto):**

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
