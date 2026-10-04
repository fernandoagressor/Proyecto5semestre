# Actualización NEXORA CORE

Esta versión conserva la arquitectura, autenticación, permisos, aislamiento por empresa y reglas de negocio existentes. La actualización reorganiza la experiencia desde el ingreso hasta la creación de una reserva.

## Flujo implementado

1. El usuario inicia sesión en la nueva vista de acceso.
2. El Super Admin entra directamente a la consola global.
3. Desde **Nueva empresa** completa un asistente de cuatro pasos:
   - información de la empresa;
   - administrador inicial y contraseña temporal;
   - módulos y límites operativos;
   - revisión, activación y confirmación.
4. El administrador inicial cambia obligatoriamente la contraseña temporal.
5. El Dashboard guía la configuración del primer proyecto e inventario.
6. La acción **Crear cliente y vincular lote** ejecuta cuatro pasos:
   - datos del cliente;
   - proyecto, etapa, manzana y lote disponible;
   - condiciones de la reserva;
   - revisión y confirmación.

El CRM técnico se conserva para trazabilidad, pero ya no es necesario crear manualmente un prospecto y una oportunidad. El endpoint `POST /api/v1/ventas/cliente-lote` crea cliente, prospecto calificado, oportunidad, vínculo con el lote y reserva dentro de una sola transacción. Ante cualquier error se revierte la operación completa.

## Base de datos

Flyway incluye `V22__modulos_configurables_por_empresa.sql`. Al iniciar el backend se crea la configuración de módulos y todas las empresas existentes reciben los módulos actuales habilitados, sin pérdida de datos.

Antes de desplegar en un ambiente productivo se recomienda realizar el respaldo normal de la base de datos. No es necesario ejecutar SQL manualmente.

## Configuración por empresa

El Super Admin puede activar o desactivar:

- Administración y seguridad.
- Proyectos e inventario.
- Clientes y ventas.
- Reservas y contratos.
- Cartera y pagos.
- Portal y servicio.
- Gerencia y reportes.

Administración y seguridad permanece siempre habilitado. La navegación y las rutas del frontend respetan la configuración de la empresa y los permisos del usuario.

## Inventario escalable

La selección de lotes no utiliza mapas. Consulta únicamente lotes `DISPONIBLE` y permite filtrar en el backend por:

- proyecto;
- etapa;
- manzana;
- código, número o ubicación;
- área mínima y máxima;
- precio mínimo y máximo.

Los resultados se paginan de 25 en 25. La disponibilidad se valida nuevamente con bloqueo transaccional en el momento de confirmar la reserva.

## Ejecución

El despliegue sigue usando los mismos comandos del proyecto. Las credenciales de MySQL ahora leen `DB_USER` y `DB_PASSWORD`, conservando `root/root` como valores locales por defecto.

```bash
docker compose up --build
```

Para validar solamente el frontend:

```bash
cd frontend
npm ci
npm run build
npm run test:ci
```

## Validaciones de esta entrega

- Compilación de producción de Angular completada.
- Verificación TypeScript sin errores.
- 46 pruebas frontend aprobadas en 17 archivos.
- Prueba nueva del contrato HTTP del flujo cliente-lote.
- Prueba backend nueva del orquestador transaccional.

El entorno usado para preparar esta entrega no dispone de Maven 3.9 ni Java 21 completos, por lo que las pruebas Java deben ejecutarse en Docker o en el entorno habitual del proyecto antes del despliegue definitivo.
