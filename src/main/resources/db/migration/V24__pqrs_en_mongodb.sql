-- Rama 5: las PQRS pasan a MongoDB (colección solicitudes_pqrs).
--
-- 1. citas_servicio.solicitud_id deja de apuntar a una tabla de MySQL: las solicitudes nuevas
--    solo existen en MongoDB. CitaServicioService valida que la solicitud exista y sea del cliente.
-- 2. Se siembran las tablas espejo con las PQRS que ya había en MySQL, para que el dashboard
--    (que ahora lee lectura_*) muestre el histórico desde el primer arranque. Lo que cambie
--    después lo mantiene el Change Stream.
--
-- Las tablas solicitudes_pqrs, solicitud_mensajes, solicitud_adjuntos, solicitud_historial y
-- encuestas_satisfaccion se conservan sin escrituras como respaldo; se retiran en la rama 8.

SET NAMES utf8mb4;

ALTER TABLE `citas_servicio`
  DROP FOREIGN KEY `fk_citas_servicio_solicitud_id_solicitudes_pqrs`;

INSERT INTO `lectura_solicitudes_pqrs`
  (`id`, `empresa_id`, `proyecto_id`, `tipo`, `estado`, `fecha_limite`,
   `fecha_primera_respuesta`, `creado_en`)
SELECT s.`id`, s.`empresa_id`, s.`proyecto_id`, s.`tipo`, s.`estado`, s.`fecha_limite`,
       s.`fecha_primera_respuesta`, s.`creado_en`
FROM `solicitudes_pqrs` s;

-- Una encuesta por solicitud (la más reciente), igual que en el documento de MongoDB.
INSERT INTO `lectura_encuestas_satisfaccion`
  (`solicitud_id`, `empresa_id`, `proyecto_id`, `puntuacion`, `respondida_en`)
SELECT e.`solicitud_id`, s.`empresa_id`, s.`proyecto_id`, e.`puntuacion`, e.`respondida_en`
FROM `encuestas_satisfaccion` e
JOIN `solicitudes_pqrs` s ON s.`id` = e.`solicitud_id`
WHERE e.`id` = (
  SELECT MAX(e2.`id`) FROM `encuestas_satisfaccion` e2 WHERE e2.`solicitud_id` = e.`solicitud_id`
);
