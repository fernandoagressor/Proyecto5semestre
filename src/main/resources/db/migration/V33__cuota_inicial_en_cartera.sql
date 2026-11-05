-- La parte de la cuota inicial que no cubrió la reserva se cobra en Cartera como la cuota 0 del
-- plan de pagos (vence el día de la firma). Hasta ahora las cuotas empezaban en 1.
ALTER TABLE `cuotas` DROP CHECK `ck_cuotas_1`;
ALTER TABLE `cuotas` ADD CONSTRAINT `ck_cuotas_1` CHECK (`numero` >= 0);
