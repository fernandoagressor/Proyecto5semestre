-- Inventario por pasos: una etapa se crea con N manzanas y una manzana con N lotes de una vez.
-- Esos lotes nacen en estado PENDIENTE ("por completar"), sin área ni precio, y pasan solos a
-- DISPONIBLE cuando se les registran medidas y precio. Un lote PENDIENTE no se puede reservar,
-- vender ni aparece en el catálogo público (que solo lista DISPONIBLES).
SET NAMES utf8mb4;

ALTER TABLE `lotes`
  DROP CHECK `ck_lotes_1`,
  DROP CHECK `ck_lotes_2`,
  DROP CHECK `ck_lotes_3`;

ALTER TABLE `lotes`
  MODIFY `area_m2` DECIMAL(12,2) NULL,
  MODIFY `valor_lista` DECIMAL(19,2) NULL;

ALTER TABLE `lotes`
  ADD CONSTRAINT `ck_lotes_1` CHECK (`area_m2` IS NULL OR `area_m2` > 0),
  ADD CONSTRAINT `ck_lotes_2` CHECK (`valor_lista` IS NULL OR `valor_lista` >= 0),
  ADD CONSTRAINT `ck_lotes_3` CHECK (`estado` IN ('PENDIENTE','DISPONIBLE','RESERVADO','VENDIDO','BLOQUEADO')),
  ADD CONSTRAINT `ck_lotes_datos_completos`
    CHECK (`estado` = 'PENDIENTE' OR (`area_m2` IS NOT NULL AND `valor_lista` IS NOT NULL));
