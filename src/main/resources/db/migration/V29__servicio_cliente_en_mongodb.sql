-- Rama 10: citas de servicio, comunicaciones con clientes y base de conocimiento (categorías y
-- artículos) pasan a MongoDB.
--
-- 1. Se suelta la llave artículos -> categorías: las categorías nuevas solo existen en MongoDB.
-- 2. Se siembra lectura_citas_servicio con el histórico: el dashboard de servicio cuenta las
--    citas próximas desde esa tabla espejo, que desde ahora mantiene el Change Stream.
--
-- solicitudes_cambio_datos se queda en MySQL: aprobarla cambia la persona y el usuario (identidad)
-- en la misma transacción.
--
-- Las tablas migradas se conservan sin escrituras como respaldo hasta la rama de retiro. Los
-- migradores copian su histórico a MongoDB al arrancar.

SET NAMES utf8mb4;

ALTER TABLE `articulos_conocimiento`
  DROP FOREIGN KEY `fk_articulos_conocimiento_categoria_id_categorias_c_496aefce`;

INSERT INTO `lectura_citas_servicio` (`id`, `empresa_id`, `proyecto_id`, `estado`, `fecha_inicio`)
SELECT c.`id`, c.`empresa_id`, c.`proyecto_id`, c.`estado`, c.`fecha_inicio`
FROM `citas_servicio` c;
