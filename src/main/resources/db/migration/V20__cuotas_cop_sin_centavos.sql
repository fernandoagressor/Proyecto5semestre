-- NEXORA CORE
-- Migracion V20: normalizacion de cronogramas COP a pesos enteros.
-- Compatible con MySQL 8. No modifica V1-V19.

SET NAMES utf8mb4;
SET time_zone = '-05:00';

-- Los planes a tasa cero creados por V19 podian dividir el capital con
-- centavos que la interfaz COP no mostraba. Solo se corrigen planes activos
-- completos y seguros: capital entero, sin intereses ni otros conceptos y
-- sin pagos que superen el nuevo valor entero de su cuota.
CREATE TEMPORARY TABLE `tmp_planes_cop_enteros` (
  `plan_pago_id` BIGINT UNSIGNED NOT NULL,
  `numero_cuotas` INT UNSIGNED NOT NULL,
  `cuota_base` DECIMAL(19,2) NOT NULL,
  PRIMARY KEY (`plan_pago_id`)
);

INSERT INTO `tmp_planes_cop_enteros`
(`plan_pago_id`, `numero_cuotas`, `cuota_base`)
SELECT
  pp.`id`,
  pp.`numero_cuotas`,
  CAST(FLOOR(pp.`capital_financiado` / pp.`numero_cuotas`) AS DECIMAL(19,2))
FROM `planes_pago` pp
WHERE pp.`estado` = 'ACTIVO'
  AND pp.`tasa_efectiva_anual` = 0
  AND pp.`capital_financiado` = ROUND(pp.`capital_financiado`, 0)
  AND (
    SELECT COUNT(*)
    FROM `cuotas` q
    WHERE q.`plan_pago_id` = pp.`id`
  ) = pp.`numero_cuotas`
  AND NOT EXISTS (
    SELECT 1
    FROM `cuotas` q
    WHERE q.`plan_pago_id` = pp.`id`
      AND (
        q.`interes` <> 0
        OR q.`otros_conceptos` <> 0
        OR q.`interes_mora` <> 0
        OR q.`estado` IN ('CANCELADA', 'CONDONADA')
      )
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `cuotas` q
    WHERE q.`plan_pago_id` = pp.`id`
      AND q.`valor_pagado` > CASE
        WHEN q.`numero` = pp.`numero_cuotas`
          THEN pp.`capital_financiado`
            - CAST(FLOOR(pp.`capital_financiado` / pp.`numero_cuotas`) AS DECIMAL(19,2))
              * (pp.`numero_cuotas` - 1)
        ELSE CAST(FLOOR(pp.`capital_financiado` / pp.`numero_cuotas`) AS DECIMAL(19,2))
      END
  )
  AND EXISTS (
    SELECT 1
    FROM `cuotas` q
    WHERE q.`plan_pago_id` = pp.`id`
      AND (q.`capital` <> ROUND(q.`capital`, 0) OR q.`saldo` <> ROUND(q.`saldo`, 0))
  );

UPDATE `cuotas` q
JOIN `planes_pago` pp ON pp.`id` = q.`plan_pago_id`
JOIN `tmp_planes_cop_enteros` t ON t.`plan_pago_id` = q.`plan_pago_id`
SET
  q.`capital` = CASE
    WHEN q.`numero` = t.`numero_cuotas`
      THEN pp.`capital_financiado` - t.`cuota_base` * (t.`numero_cuotas` - 1)
    ELSE t.`cuota_base`
  END,
  q.`valor_programado` = CASE
    WHEN q.`numero` = t.`numero_cuotas`
      THEN pp.`capital_financiado` - t.`cuota_base` * (t.`numero_cuotas` - 1)
    ELSE t.`cuota_base`
  END,
  q.`saldo` = CASE
    WHEN q.`numero` = t.`numero_cuotas`
      THEN pp.`capital_financiado` - t.`cuota_base` * (t.`numero_cuotas` - 1)
    ELSE t.`cuota_base`
  END - q.`valor_pagado`,
  q.`estado` = CASE
    WHEN (
      CASE
        WHEN q.`numero` = t.`numero_cuotas`
          THEN pp.`capital_financiado` - t.`cuota_base` * (t.`numero_cuotas` - 1)
        ELSE t.`cuota_base`
      END
    ) - q.`valor_pagado` = 0 THEN 'PAGADA'
    WHEN CURRENT_DATE() > q.`fecha_vencimiento` THEN 'VENCIDA'
    WHEN q.`valor_pagado` > 0 THEN 'PARCIAL'
    ELSE 'PENDIENTE'
  END,
  q.`dias_mora` = CASE
    WHEN (
      CASE
        WHEN q.`numero` = t.`numero_cuotas`
          THEN pp.`capital_financiado` - t.`cuota_base` * (t.`numero_cuotas` - 1)
        ELSE t.`cuota_base`
      END
    ) - q.`valor_pagado` = 0 THEN 0
    ELSE q.`dias_mora`
  END,
  q.`version` = q.`version` + 1;

DROP TEMPORARY TABLE `tmp_planes_cop_enteros`;
