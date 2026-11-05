-- Firma del contrato desde el portal del cliente: el cliente sube el contrato firmado, queda
-- PENDIENTE de revisión y alguien de Ventas lo confirma (activa la venta) o lo rechaza con motivo.
-- Se guarda el archivo de cada firma, de dónde vino y quién la revisó.
ALTER TABLE `firmas_contrato`
  ADD COLUMN `archivo_id` BIGINT UNSIGNED NULL AFTER `estado_validacion`,
  ADD COLUMN `origen` VARCHAR(20) NULL AFTER `archivo_id`,
  ADD COLUMN `motivo_rechazo` VARCHAR(400) NULL AFTER `origen`,
  ADD COLUMN `revisada_por` BIGINT UNSIGNED NULL AFTER `motivo_rechazo`,
  ADD COLUMN `revisada_en` DATETIME(6) NULL AFTER `revisada_por`,
  ADD CONSTRAINT `fk_firmas_contrato_archivo_id_archivos`
    FOREIGN KEY (`archivo_id`) REFERENCES `archivos` (`id`),
  ADD CONSTRAINT `ck_firmas_contrato_origen`
    CHECK (`origen` IS NULL OR `origen` IN ('OFICINA', 'PORTAL'));
