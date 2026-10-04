# Decimocuarto corte: Portal y Servicio al Cliente Operativo

Fecha: 12 de agosto de 2026  
Base validada: Corte 13 R3  
Stack: Java 21, Spring Boot 3.5, MySQL 8.4 y Angular 20

## Alcance

El Corte 14 sustituye la pantalla provisional de Servicio al Cliente por una
operación conectada a los endpoints reales del backend:

1. Resumen operativo con solicitudes abiertas, casos fuera de SLA, prioridad
   crítica y próximas citas.
2. Bandeja de PQRS con filtros por tipo y estado, búsqueda local y alcance por
   empresa, proyecto, responsable o cliente autenticado.
3. Radicación de peticiones, quejas, reclamos, sugerencias y solicitudes con
   prioridad y fecha límite automática.
4. Conversación cliente–equipo, notas internas invisibles para el cliente,
   adjuntos y trazabilidad de estados.
5. Carga directa de PDF, PNG y JPG desde una solicitud, sin conceder al cliente
   permisos generales de gestión documental.
6. Agenda de servicio para asesorías, visitas, entregas, firmas y reuniones,
   con reprogramación, confirmación, cancelación y prevención de cruces.
7. Portal privado del cliente con resumen contractual, documentos disponibles,
   solicitudes abiertas, próxima cita y actualización controlada de datos.
8. Interfaz responsive, estados de carga, retroalimentación de operaciones y
   autorización visual alineada con la validación final del backend.

## Migración V21

`V21__operacion_portal_servicio_cliente.sql` agrega índices compuestos para las
bandejas de PQRS, agenda y adjuntos. No modifica datos de negocio ni altera las
migraciones `V1–V20`.

## Nuevo endpoint de adjuntos

```text
POST /api/v1/solicitudes/{id}/adjuntos/archivo
GET  /api/v1/solicitudes/{id}/adjuntos/{archivoId}/descargar
GET  /api/v1/portal/documentos/{archivoId}/descargar
```

El endpoint exige `SOLICITUDES_RESPONDER`, valida primero el acceso a la
solicitud y después almacena el archivo como confidencial. La respuesta incluye
nombre, tipo MIME y tamaño para mostrarlo inmediatamente en el historial.
Las descargas vuelven a comprobar la propiedad de la solicitud o la visibilidad
del documento en el portal; no dependen del alcance interno de proyectos usado
por los colaboradores.

## Prueba funcional sugerida

1. Iniciar sesión con un usuario de Servicio al Cliente.
2. Abrir **Servicio al cliente** y crear una solicitud para un cliente activo.
3. Abrirla, enviar una respuesta y registrar una nota interna.
4. Adjuntar un PDF y comprobar su aparición sin recargar.
5. Cambiar el estado a `EN_PROCESO` y revisar el historial.
6. Programar una cita relacionada y reprogramarla.
7. Iniciar sesión como el cliente relacionado y comprobar que solo ve sus
   solicitudes, mensajes públicos, citas, documentos y cambios de datos.

Las cargas deben analizarse con antivirus y almacenarse en infraestructura
privada antes de una salida a producción. Las notificaciones por correo, SMS o
WhatsApp requieren los adaptadores del proveedor elegido; el módulo no simula
entregas externas.
