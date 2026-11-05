# Servidor económico en AWS Lightsail (~USD 27 al mes)

NEXORA CORE completo en un solo servidor Lightsail de 4 GiB: MySQL, MongoDB, Redis, la API,
el frontend, Caddy con HTTPS, el monitoreo (Prometheus y Grafana) y los respaldos diarios a S3.
Es el mismo `compose.prod.yaml` de [DESPLIEGUE_AWS.md](DESPLIEGUE_AWS.md), con la capacidad de
[`deploy/capacidad-4gb.env`](../deploy/capacidad-4gb.env). El CI levanta el stack de producción
con ese perfil y falla si los contenedores pasan de 3000 MiB o si alguno se cierra por memoria.
En la primera corrida, después de la prueba de humo, todo el stack usó 1279 MiB de 4096.

## Costo mensual

| Concepto | USD/mes |
| --- | --- |
| Lightsail Linux **Medium** con IPv4: 2 vCPU, 4 GiB, 80 GB de disco y 4 TB de tráfico | 24,00 |
| IP estática (gratis mientras esté conectada a la instancia) | 0,00 |
| Snapshots automáticos diarios (7 días, se cobra lo usado a USD 0,05 por GB) | ~1 a 3 |
| Respaldos en S3 (30 días, ~60 MB cada uno) | ~0,10 |
| **Total aproximado** | **~25 a 27** |

Comparación: la m7i.xlarge de staging (4 vCPU, 16 GiB) con su disco de 60 GB y su Elastic IP
cuesta del orden de USD 150 al mes encendida todo el tiempo.

**Qué tan lejos llega.** La CPU de los planes Lightsail es "con ráfagas". El plan de 4 GiB
sostiene un 20 % de sus 2 vCPU todo el tiempo (0,4 vCPU) y acumula crédito para picos. En
staging, la prueba de carga de 100 usuarios usó cerca del 8 % de 4 vCPU (~0,3 vCPU), así que el
uso diario de la empresa entra holgado. Señal para subir al plan de 8 GiB (USD 44, 30 %
sostenido): en Lightsail → **Metrics → CPU utilization**, si la gráfica pasa seguido a la zona
de ráfaga y el crédito (*burst capacity*) baja a cero. También si Grafana muestra el swap en uso
todo el día. Para subir de plan se saca un snapshot y se crea una instancia nueva más grande
desde él: la IP estática se pasa a la nueva y no hay que mover datos.

Antes de una prueba de carga grande (k6 `estres` o `pico`), revise que haya crédito de ráfaga.
Si no lo hay, los resultados salen peores de lo que sería el uso real.

## 1. Crear la instancia

En la consola de AWS → **Lightsail** (región **Ohio, us-east-2**, la misma del bucket de respaldos):

1. **Create instance** → *Linux/Unix* → *OS Only* → **Ubuntu 24.04 LTS**.
2. **SSH key pair**: cree una nueva (`nexora-lightsail`) y descargue el `.pem` a
   `C:\Users\<usted>\.ssh\`. No la comparta por chat.
3. **Network type**: *Dual-stack*. **Plan**: **USD 24 (4 GB RAM, 2 vCPUs, 80 GB SSD)**.
4. Nombre: `nexora-produccion` → **Create instance**.
5. **Networking → Create static IP** → conéctela a `nexora-produccion`. Una IP estática sin
   instancia conectada sí se cobra: si borra la instancia, borre también la IP.
6. **Networking → IPv4 Firewall** de la instancia:
   - `SSH 22`: **Restrict to IP address** → la IP del equipo. Marque **Allow Lightsail
     browser SSH**: así se puede entrar desde la consola aunque cambie la IP de la casa (el
     problema del *Connection timed out*).
   - `HTTP 80` y `HTTPS 443`: cualquier origen. Opcional: `UDP 443` para HTTP/3.
7. **Snapshots → Automatic snapshots → Enable**, a las 08:00 UTC (03:00 en Colombia). Lightsail
   guarda los últimos 7. Es la segunda capa de respaldo, además de S3.

Entrar desde PowerShell:

```powershell
ssh -i "$HOME\.ssh\nexora-lightsail.pem" ubuntu@<IP_ESTATICA>
```

## 2. Llave de AWS solo para los respaldos

Lightsail no admite el rol `nexora-ec2` de EC2, así que el servidor usa una llave de un usuario de
IAM que solo puede subir y bajar respaldos (la política `nexora-respaldos` no permite borrar).

1. **IAM → Users → Create user** `nexora-respaldos-servidor`, sin acceso a la consola.
2. **Add permissions → Attach policies directly** → `nexora-respaldos`.
3. En el usuario: **Security credentials → Create access key** → *Application running outside
   AWS*. Copie la llave **directo al servidor** (paso 3) y guárdela en el gestor de contraseñas
   del equipo, nunca en el chat ni en el repositorio.

## 3. Preparar el servidor

```bash
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a apt-get upgrade -y
sudo apt-get install -y jq git cron
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
sudo snap install aws-cli --classic
sudo mkdir -p /opt/nexora && sudo chown ubuntu:ubuntu /opt/nexora
git clone --branch develop https://github.com/fernandoagressor/Proyecto5semestre.git /opt/nexora
sudo bash /opt/nexora/deploy/crear-swap.sh      # 2 GiB de swap, queda tras reinicios
aws configure                                   # llave del paso 2; región us-east-2; salida json
chmod 600 ~/.aws/credentials
aws s3 ls s3://<BUCKET>/respaldos/ | tail -3    # debe listar los respaldos
sudo reboot                                     # vuelva a entrar en un minuto
```

## 4a. Pasar los datos desde el servidor EC2 actual

En el servidor **viejo** (EC2):

```bash
cd /opt/nexora
bash deploy/respaldar.sh                        # al final imprime el nombre nexora-AAAAMMDD-HHMMSS
grep '^NEXORA_VERSION=' .env                    # versión que corre hoy
```

El `.env` del servidor nuevo debe tener **las mismas contraseñas**: los usuarios de MySQL y
MongoDB vienen dentro del respaldo. Cópielo de servidor a servidor pasando por su PC y bórrelo
del PC al terminar:

```powershell
scp -i "$HOME\.ssh\nexora-staging.pem" ubuntu@13.58.119.98:/opt/nexora/.env "$HOME\nexora.env"
scp -i "$HOME\.ssh\nexora-lightsail.pem" "$HOME\nexora.env" ubuntu@<IP_ESTATICA>:/opt/nexora/.env
Remove-Item "$HOME\nexora.env"
```

En el servidor **nuevo**:

```bash
cd /opt/nexora
chmod 600 .env
cat deploy/capacidad-4gb.env >> .env            # perfil de 4 GiB (los últimos valores mandan)
sed -i 's/^DOMINIO=.*/DOMINIO=<nuevo dominio>/' .env
sed -i 's/^BOOTSTRAP_ADMIN_ENABLED=.*/BOOTSTRAP_ADMIN_ENABLED=false/' .env
bash deploy/instalar-respaldos.sh               # carpetas, prueba de S3 y cron
bash deploy/desplegar.sh <NEXORA_VERSION del servidor viejo>
bash deploy/restaurar.sh s3://<BUCKET>/respaldos/nexora-AAAAMMDD-HHMMSS --confirmar --sin-respaldo-previo
```

**Dominio.** Con `sslip.io`, el dominio sale de la IP: con la IP estática `3.12.34.56` queda
`3-12-34-56.sslip.io`. Con un dominio propio, cambie el registro A a la IP estática *antes* de
desplegar, porque Caddy pide el certificado al arrancar. Actualice también la variable `DOMINIO`
del entorno de GitHub.

## 4b. Servidor desde cero (sin datos que pasar)

Siga la sección 6 de [DESPLIEGUE_AWS.md](DESPLIEGUE_AWS.md): genera el `.env` con contraseñas
nuevas y crea el administrador inicial. Antes de `desplegar.sh`, agregue
`cat deploy/capacidad-4gb.env >> .env`. Después siga con la sección 11 (respaldos) a partir del
paso 5.

## 5. Comprobar

```bash
cd /opt/nexora
docker compose ps                                # todo healthy
BASE_URL=https://<DOMINIO> ADMIN_USUARIO=SUPERADMIN ADMIN_PASSWORD='<contraseña>' COMPOSE=1 \
  bash scripts/prueba-humo.sh                    # "Prueba de humo superada"
docker stats --no-stream                         # suma de MEM USAGE entre 1,5 y 2,5 GiB
free -h                                          # swap casi sin usar
bash deploy/probar-restauracion.sh               # "Prueba de restauración superada"
```

Grafana se abre igual que antes, con un túnel SSH:
`ssh -i "$HOME\.ssh\nexora-lightsail.pem" -L 3000:127.0.0.1:3000 ubuntu@<IP_ESTATICA>` y luego
http://localhost:3000.

## 6. Apagar lo que cobra en EC2

Hágalo solo cuando el servidor nuevo pase la sección 5. Los respaldos de S3 se quedan.

1. **EC2 → Instances** → la m7i.xlarge → **Instance state → Terminate**. Su disco raíz se borra
   con ella.
2. **EC2 → Elastic IPs** → `13.58.119.98` → **Release**. Una Elastic IP suelta se sigue cobrando.
3. **EC2 → Volumes**: borre los que queden en estado *available*.
4. **EC2 → Snapshots** y **Lifecycle Manager**: borre los snapshots y las políticas de EBS que se
   hayan creado.
5. **EC2 → AMIs**: anule el registro de las imágenes propias, si hay.
6. Revise que no haya **NAT gateways** (VPC), **load balancers** ni **bases RDS** creados por
   pruebas. Cualquiera de ellos cuesta más que todo este servidor.
7. **Billing → Budgets → Create budget**: *Monthly cost budget* de **USD 30**, con aviso por
   correo al 80 % y al 100 %. Si usan los proyectos de la nueva experiencia de AWS, ponga el
   límite de gasto del proyecto en USD 35 **sin** pausa automática, para que producción no se
   apague sola.

En el siguiente ciclo de facturación, **Billing → Bills** debe mostrar casi solo *Lightsail*
(~USD 24) y centavos de *S3*.

## Staging y pruebas de carga

Para no pagar un segundo servidor fijo, staging se crea solo cuando haga falta:
**Snapshots → Create new instance** desde el último snapshot de producción, en el plan que se
quiera probar. Al terminar se borran la instancia y su IP estática. Una instancia de USD 24 que
vive 2 días cuesta menos de USD 2. Los datos de una copia de producción son reales: no corra
`carga/semilla.js` contra producción ni comparta esa copia.
