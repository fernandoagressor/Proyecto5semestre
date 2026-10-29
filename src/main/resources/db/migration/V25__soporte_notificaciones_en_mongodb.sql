-- Rama 6: tickets de soporte y notificaciones pasan a MongoDB
-- (colecciones tickets_soporte y notificaciones).
--
-- Ninguna tabla que se queda en MySQL apunta a estas, así que no hay llaves que soltar.
-- Se siembra la tabla espejo de tickets con el histórico, para que el dashboard (que ahora lee
-- lectura_tickets_soporte) muestre los tickets existentes desde el primer arranque; lo que cambie
-- después lo mantiene el Change Stream. Las notificaciones no tienen espejo: el dashboard no las usa.
--
-- tickets_soporte, ticket_mensajes, ticket_adjuntos, ticket_historial, notificaciones y
-- envios_notificacion se conservan sin escrituras como respaldo; se retiran en la rama 8.

SET NAMES utf8mb4;

INSERT INTO `lectura_tickets_soporte` (`id`, `empresa_id`, `proyecto_id`, `estado`)
SELECT t.`id`, t.`empresa_id`, t.`proyecto_id`, t.`estado`
FROM `tickets_soporte` t;
