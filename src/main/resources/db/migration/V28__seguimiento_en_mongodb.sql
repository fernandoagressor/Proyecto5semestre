-- Rama 9: tareas (con su historial embebido), gestiones de cobro y estados de cuenta pasan a
-- MongoDB (colecciones tareas, gestiones_cobro y estados_cuenta).
--
-- Ninguna tabla de MySQL tiene llaves foráneas hacia estas tres, así que no hay llaves que soltar.
-- Se siembra lectura_tareas con el histórico: el dashboard gerencial cuenta las tareas vencidas
-- desde esa tabla espejo, que desde ahora mantiene el Change Stream.
--
-- tareas, tarea_historial, gestiones_cobro y estados_cuenta_generados se conservan sin escrituras
-- como respaldo hasta la rama de retiro. Los migradores copian su histórico a MongoDB al arrancar.

SET NAMES utf8mb4;

INSERT INTO `lectura_tareas` (`id`, `empresa_id`, `proyecto_id`, `estado`)
SELECT t.`id`, t.`empresa_id`, t.`proyecto_id`, t.`estado`
FROM `tareas` t;
