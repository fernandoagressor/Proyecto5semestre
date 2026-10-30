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
         MONGO_APP_PASSWORD MONGO_MIGRATOR_PASSWORD REDIS_PASSWORD; do
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
exit   # vuelva a entrar para que el grupo docker aplique
```

## 6. Primer despliegue

Mientras el CI no publique las imágenes en GHCR (bloque P3), el servidor las construye desde el
código:

```bash
ssh -i nexora.pem ubuntu@<ELASTIC_IP>
sudo mkdir -p /opt/nexora && sudo chown ubuntu:ubuntu /opt/nexora
git clone --branch develop https://github.com/fernandoagressor/Proyecto5semestre.git /opt/nexora
cd /opt/nexora
cp deploy/produccion.env.example .env
chmod 600 .env
nano .env    # DOMINIO, ACME_EMAIL, las contraseñas del paso 2 y BOOTSTRAP_ADMIN_ENABLED=true
```

```bash
docker compose -f compose.prod.yaml up -d --build --wait --wait-timeout 900
docker compose -f compose.prod.yaml ps
```

Todos los servicios deben quedar `healthy` (Caddy, `running`). Abra `https://<DOMINIO>`, inicie
sesión con el administrador del `.env` y cambie la contraseña cuando la aplicación lo pida.

Luego **apague la creación del administrador inicial**:

```bash
sed -i 's/^BOOTSTRAP_ADMIN_ENABLED=.*/BOOTSTRAP_ADMIN_ENABLED=false/' .env
docker compose -f compose.prod.yaml up -d
```

## 7. Prueba de humo en el servidor

```bash
cd /opt/nexora
BASE_URL=https://<DOMINIO> ADMIN_USUARIO=SUPERADMIN \
ADMIN_PASSWORD='<contraseña actual>' COMPOSE=1 COMPOSE_FILE=compose.prod.yaml \
bash scripts/prueba-humo.sh
```

Debe terminar con "Prueba de humo superada". Crea un proyecto, un prospecto, una tarea y un
artículo con la marca `HUMO`/`humo`; en staging no estorban.

## 8. Operación diaria

| Para | Comando (en `/opt/nexora`) |
| --- | --- |
| Ver el estado | `docker compose -f compose.prod.yaml ps` |
| Ver logs de la API | `docker compose -f compose.prod.yaml logs -f --tail 200 api` |
| Actualizar a lo último de `develop` | `git pull && docker compose -f compose.prod.yaml up -d --build --wait` |
| Reiniciar la API | `docker compose -f compose.prod.yaml restart api` |
| Volver a una versión anterior | `git checkout <commit o tag> && docker compose -f compose.prod.yaml up -d --build --wait` |

Para no escribir `-f compose.prod.yaml` cada vez: `echo 'export COMPOSE_FILE=compose.prod.yaml' >> ~/.bashrc`.

## Seguridad: qué queda expuesto

- Solo los puertos 80 y 443 (Caddy) y 22 desde la IP del equipo. MySQL, MongoDB, Redis, la API y
  el frontend no publican puertos: solo se hablan dentro de la red de Docker.
- De `/actuator` solo responde `/actuator/health`; el resto devuelve 404.
- HTTPS con HSTS. La IP real de cada cliente llega a la API desde Caddy, así el límite de 20
  intentos de inicio de sesión fallidos por IP no se puede engañar.
- `.env` con permisos `600` solo en el servidor; nunca en el repositorio.
