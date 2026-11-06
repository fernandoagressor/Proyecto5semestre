# Producción en AWS: 3 capas por ~USD 48 al mes

Cuenta de AWS **nueva y aparte**, solo para producción. La cuenta actual (`nexora.staging`)
queda para pruebas. Producción arranca **vacía**: solo con el administrador inicial.

```
                         Internet (HTTPS)
                               │
  ┌──────────── VPC por defecto · Ohio (us-east-2) ─────────────┐
  │  Grupo nexora-web: 80 y 443 abiertos; 22 solo la IP del equipo│
  │  ┌─ EC2 t3a.medium (2 vCPU, 4 GB) · IP fija ──────────────┐  │
  │  │ 1) Presentación: Caddy (HTTPS) + frontend Angular      │  │
  │  │ 2) Aplicación:   API Spring Boot                       │  │
  │  │    MongoDB y Redis en contenedores · monitoreo         │  │
  │  └──────────┬──────────────────────────────┬──────────────┘  │
  │             │ 3306, solo desde nexora-web  │ rol IAM (sin claves)
  │  ┌─ 3) Datos: RDS MySQL 8.4 ─┐   ┌─ S3 (privado, cifrado) ─────┐ │
  │  │ db.t4g.micro · 20 GB gp3  │   │ archivos/  contratos, fotos │ │
  │  │ sin acceso público        │   │ respaldos/ MongoDB y MySQL  │ │
  │  │ respaldos automáticos 7 d │   │            (30 días)        │ │
  │  └───────────────────────────┘   └─────────────────────────────┘ │
  └──────────────────────────────────────────────────────────────┘
```

**¿Por qué MongoDB y Redis siguen en la EC2?** Sus versiones administradas en AWS (DocumentDB y
ElastiCache) cuestan más de USD 60 y unos USD 12 al mes. MySQL, que guarda ventas, contratos y
cartera, sí va a RDS. MongoDB se respalda todos los días en S3.

## Costo mensual (Ohio, sin compromisos)

| Recurso | USD/mes |
| --- | --- |
| EC2 t3a.medium (crédito de CPU *Standard*) | ~27,50 |
| Disco de la EC2, 30 GB gp3 | ~2,40 |
| IP pública fija | ~3,65 |
| RDS MySQL db.t4g.micro, una zona | ~12,00 |
| Disco de RDS, 20 GB gp3 (los respaldos de 7 días no cobran hasta 20 GB) | ~2,30 |
| S3: archivos y respaldos (pocos GB) | ~0,50 |
| **Total** | **~48** |

Lo que **no** se activa porque cobra aparte: Multi-AZ, Performance Insights de pago, monitoreo
mejorado de RDS, Secrets Manager, balanceador de carga y NAT Gateway.

---

## 1. Crear la cuenta de producción

1. Cree la cuenta con un **correo del equipo** (no personal).
2. En el plan elija **Plan pago (*Paid plan*)**. ⚠️ El *plan gratuito* **cierra la cuenta a los 6
   meses** o cuando se acaban los créditos; producción no puede cerrarse. Los créditos de bienvenida
   se usan igual con el plan pago.
3. **Usuario raíz → Credenciales de seguridad → Asignar dispositivo MFA** (app autenticadora).
4. **Facturación → Presupuestos → Crear presupuesto** → *Presupuesto de costos mensual* de
   **USD 50**, con avisos por correo al **90 % (USD 45)** y al **100 %**. Si la cuenta usa proyectos
   con límite de gasto, póngalo en USD 60 **sin** pausa automática: un límite que pausa apaga
   producción.
5. Arriba a la derecha, región **Ohio (us-east-2)**. Todo lo de esta guía va en Ohio.

## 2. Bucket de S3 (archivos y respaldos)

1. **S3 → Crear bucket** → nombre `nexora-produccion-<algo único>` (por ejemplo,
   `nexora-produccion-altamar`), región Ohio.
2. Deje **Bloquear todo el acceso público** marcado y el cifrado **SSE-S3**. Active el
   **control de versiones**: si se borra un archivo por error, se puede recuperar.
3. En el bucket, **Administración → Crear regla de ciclo de vida**:
   - `respaldos-30-dias`: prefijo `respaldos/` → *Vencer versiones actuales* a los **30 días** y
     *Eliminar permanentemente versiones no actuales* a los **7 días**.
   - `archivos-versiones`: prefijo `archivos/` → *Eliminar permanentemente versiones no actuales*
     a los **30 días** (solo las versiones viejas; los archivos vigentes no se tocan).
   - En las dos: *Eliminar cargas multiparte incompletas* a los 7 días.

## 3. Rol de la EC2 (sin claves en el servidor)

1. **IAM → Políticas → Crear política → JSON** (cambie `<BUCKET>`), nombre `nexora-produccion-s3`:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Sid": "Archivos", "Effect": "Allow",
         "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
         "Resource": "arn:aws:s3:::<BUCKET>/archivos/*" },
       { "Sid": "Respaldos", "Effect": "Allow",
         "Action": ["s3:PutObject", "s3:GetObject"],
         "Resource": "arn:aws:s3:::<BUCKET>/respaldos/*" },
       { "Sid": "ListarRespaldos", "Effect": "Allow", "Action": "s3:ListBucket",
         "Resource": "arn:aws:s3:::<BUCKET>",
         "Condition": { "StringLike": { "s3:prefix": ["respaldos/*"] } } }
     ]
   }
   ```

   La aplicación puede subir, leer y borrar archivos. El servidor puede subir y bajar respaldos,
   pero **no borrarlos**: los borra solo la regla de 30 días.
2. **IAM → Roles → Crear rol** → *Servicio de AWS* → *EC2* → política `nexora-produccion-s3` →
   nombre `nexora-produccion-ec2`.

## 4. Servidor EC2

**EC2 → Lanzar instancias**:

| Campo | Valor |
| --- | --- |
| Nombre | `nexora-produccion` |
| Imagen | Ubuntu Server 24.04 LTS (x86) |
| Tipo | **t3a.medium** |
| Par de claves | Nuevo: `nexora-produccion` (descargue el `.pem` y guárdelo en `C:\Users\<usted>\.ssh\`) |
| Red | VPC por defecto · *Asignar IP pública automáticamente*: sí |
| Grupo de seguridad | Nuevo: `nexora-web` · SSH 22 desde **Mi IP** · HTTP 80 y HTTPS 443 desde cualquier lugar |
| Almacenamiento | **30 GiB gp3** |
| Detalles avanzados → Perfil de instancia de IAM | `nexora-produccion-ec2` |
| Detalles avanzados → Especificación de crédito | **Estándar** (sin "Ilimitado": si la CPU se usa a tope, va más lento en vez de cobrar extra) |

Después: **EC2 → Direcciones IP elásticas → Asignar** → **Asociar** a `nexora-produccion`. Esa es
la IP fija; con sslip.io el dominio queda `1-2-3-4.sslip.io` (los puntos de la IP cambian a
guiones).

## 5. Base de datos RDS

### 5a. Grupo de parámetros (una vez)

**RDS → Grupos de parámetros → Crear** → familia **mysql8.4**, tipo *Grupo de parámetros de base
de datos*, nombre `nexora-mysql84`. Edítelo y cambie:

| Parámetro | Valor | Por qué |
| --- | --- | --- |
| `log_bin_trust_function_creators` | `1` | Las migraciones crean triggers |
| `time_zone` | `America/Bogota` | Las fechas de SQL (mora, reportes) en hora de Colombia, como hoy |

### 5b. Crear la base

**RDS → Crear base de datos** → *Creación estándar*:

| Campo | Valor |
| --- | --- |
| Motor | MySQL, versión **8.4** (la última 8.4.x) |
| Plantilla | *Capa gratuita* si aparece; si no, *Desarrollo y pruebas* |
| Disponibilidad | **Instancia de base de datos de una sola zona** (sin Multi-AZ) |
| Identificador | `nexora-produccion` |
| Usuario maestro | `nexora_admin` · *Autoadministrada* (sin Secrets Manager) · contraseña generada por usted, solo letras y números |
| Clase | **db.t4g.micro** |
| Almacenamiento | **gp3, 20 GiB**; escalado automático hasta 50 GiB |
| Conectividad | **Conectarse a un recurso informático de EC2** → `nexora-produccion` (crea sola la regla 3306 solo desde la EC2) · **Acceso público: No** |
| Autenticación | Contraseña |
| Supervisión | Desactive *Performance Insights* de pago y la *Supervisión mejorada* |
| Configuración adicional → Nombre de base inicial | vacío (lo crea el script) |
| Grupo de parámetros | `nexora-mysql84` |
| Copias de seguridad | Automáticas, **7 días**, ventana 08:00–08:30 UTC (03:00 en Colombia) |
| Cifrado | Activado (clave por defecto `aws/rds`, sin costo) |
| Mantenimiento | Actualizaciones menores automáticas: sí · ventana domingo 09:00 UTC |
| Protección contra eliminación | **Activada** |

Tarda unos 10 minutos. Cuando diga *Disponible*, copie el **Punto de enlace** (algo como
`nexora-produccion.xxxx.us-east-2.rds.amazonaws.com`).

## 6. Preparar el servidor

```powershell
ssh -i "$HOME\.ssh\nexora-produccion.pem" ubuntu@<IP_FIJA>
```

Ya dentro (`ubuntu@ip-...$`):

```bash
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a apt-get upgrade -y
sudo apt-get install -y jq git cron
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
sudo snap install aws-cli --classic
sudo mkdir -p /opt/nexora && sudo chown ubuntu:ubuntu /opt/nexora
git clone --branch develop https://github.com/fernandoagressor/Proyecto5semestre.git /opt/nexora
sudo bash /opt/nexora/deploy/crear-swap.sh
sudo reboot        # vuelva a entrar en un minuto
```

## 7. El `.env` de producción

```bash
cd /opt/nexora
cp deploy/produccion.env.example .env && chmod 600 .env
cat deploy/capacidad-4gb.env >> .env
gen() { openssl rand -base64 36 | tr -d '/+=' | cut -c1-32; }
poner() { grep -q "^$1=" .env && sed -i "s|^$1=.*|$1=$2|" .env || echo "$1=$2" >> .env; }
for v in DB_PASSWORD DB_ROOT_PASSWORD MYSQL_MIGRATOR_PASSWORD MONGO_ROOT_PASSWORD MONGO_APP_PASSWORD \
         MONGO_MIGRATOR_PASSWORD REDIS_PASSWORD MYSQL_EXPORTER_PASSWORD GRAFANA_ADMIN_PASSWORD; do
  poner "$v" "$(gen)"
done
poner JWT_SECRET "$(openssl rand -base64 48 | tr -d '/+=')"
poner COMPOSE_FILE compose.prod.yaml:compose.monitoring.yaml:compose.rds.yaml
poner RDS_HOST '<punto de enlace de RDS>'
poner RDS_ADMIN_USER nexora_admin
poner RDS_ADMIN_PASSWORD '<contraseña maestra de RDS>'
poner ARCHIVOS_S3_BUCKET '<BUCKET>'
poner RESPALDO_S3_BUCKET '<BUCKET>'
poner AWS_REGION us-east-2
poner DOMINIO '<IP con guiones>.sslip.io'
poner ACME_EMAIL '<correo del equipo>'
poner BOOTSTRAP_ADMIN_ENABLED true
poner BOOTSTRAP_ADMIN_PASSWORD "Nx-$(gen)!"
poner BOOTSTRAP_EMPRESA_CODIGO '<código de la empresa>'
poner BOOTSTRAP_EMPRESA_NOMBRE '<nombre de la empresa>'
poner BOOTSTRAP_EMPRESA_NIT '<NIT>'
poner BOOTSTRAP_ADMIN_DOCUMENTO '<documento del administrador>'
poner BOOTSTRAP_ADMIN_NOMBRES '<nombres>'
poner BOOTSTRAP_ADMIN_APELLIDOS '<apellidos>'
poner BOOTSTRAP_ADMIN_USERNAME '<usuario del administrador>'
poner BOOTSTRAP_ADMIN_EMAIL '<correo del administrador>'
```

Copie el `.env` completo al **gestor de contraseñas del equipo** (`cat .env`). Nunca lo pegue en un
chat ni lo suba al repositorio. Ninguna contraseña de pruebas o de desarrollo se repite en
producción.

## 8. Primer despliegue

```bash
cd /opt/nexora
bash deploy/rds-preparar.sh        # crea la base y los usuarios en RDS y revisa los parámetros
bash deploy/instalar-respaldos.sh  # carpetas, prueba de S3 y cron diario
bash deploy/desplegar.sh develop   # o la versión publicada (v0.x.y)
```

`rds-preparar.sh` falla con un mensaje claro si RDS no responde (revise el punto de enlace, la
contraseña y que RDS se haya conectado a la EC2) o si falta el parámetro de los triggers.

Todos los servicios deben quedar `healthy`. Abra `https://<DOMINIO>`, entre con el administrador
del `.env` y cambie la contraseña. Luego apague la creación del administrador inicial:

```bash
sed -i 's/^BOOTSTRAP_ADMIN_ENABLED=.*/BOOTSTRAP_ADMIN_ENABLED=false/' .env
docker compose up -d
```

## 9. Comprobar

```bash
cd /opt/nexora
docker compose ps                                   # todo healthy; "mysql" es solo el cliente de RDS
BASE_URL=https://<DOMINIO> ADMIN_USUARIO='<usuario>' ADMIN_PASSWORD='<contraseña>' COMPOSE=1 \
  bash scripts/prueba-humo.sh                       # crea datos de prueba: córrala antes de cargar datos reales
bash deploy/respaldar.sh && aws s3 ls s3://<BUCKET>/respaldos/ | tail -2
bash deploy/probar-restauracion.sh
docker stats --no-stream
```

En la aplicación, suba un documento (por ejemplo, el comprobante de una reserva de prueba) y
revise en **S3 → el bucket → `archivos/`** que apareció el archivo.

> La prueba de humo crea un proyecto, lotes y clientes de prueba. Si ya cargaron datos reales, no
> la corra en producción.

## Qué cambia frente a la cuenta de pruebas

| | Pruebas (`nexora.staging`) | Producción |
| --- | --- | --- |
| MySQL | Contenedor en la EC2 | **RDS** (respaldos automáticos de 7 días, más el respaldo diario a S3) |
| Archivos subidos | Disco de la EC2 | **S3** `archivos/` (cifrado, con versiones) |
| `.env` | `COMPOSE_FILE=compose.prod.yaml:compose.monitoring.yaml` | `…:compose.rds.yaml` + `RDS_*` y `ARCHIVOS_S3_BUCKET` |
| Datos | De prueba (semilla, k6) | Reales: **nunca** correr `carga/semilla.js` ni pruebas de carga aquí |

## Respaldos y recuperación

- **RDS** guarda copias automáticas 7 días y permite volver a cualquier minuto de esos 7 días
  (*Restaurar a un momento dado*, que crea una instancia nueva).
- **`deploy/respaldar.sh`** (cron diario) guarda en `s3://<BUCKET>/respaldos/` el volcado de MySQL
  (tomado de RDS), MongoDB y el manifiesto, por 30 días. Los archivos subidos ya están en S3 con
  versiones, así que `documentos.tar.gz` queda vacío.
- **`deploy/restaurar.sh <respaldo> --confirmar`** carga el respaldo en RDS. Quita los `DEFINER` del
  volcado, porque el usuario maestro de RDS no puede crear triggers a nombre de otro usuario.

## Seguridad

- RDS no tiene IP pública y solo acepta conexiones de la EC2 (grupo de seguridad), con TLS.
- El bucket es privado y no tiene política pública; la aplicación entra con el rol de la EC2, sin
  claves guardadas.
- SSH solo desde la IP del equipo. Si cambia la IP: **EC2 → Grupos de seguridad → nexora-web →
  Editar reglas de entrada → SSH → Mi IP**.
- MFA en el usuario raíz y presupuesto con avisos.
