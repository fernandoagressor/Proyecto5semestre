-- Corte 12: habilita el flujo completo de ventas para el rol administrador.
-- V1-V17 permanecen sin modificaciones.

INSERT INTO rol_permisos (rol_id, permiso_id, concedido)
SELECT r.id, p.id, 1
FROM roles r
JOIN permisos p ON p.codigo IN (
    'CLIENTES_INACTIVAR',
    'RESERVAS_CONVERTIR_CONTRATO',
    'PAGOS_CONFIRMAR',
    'PAGOS_RECHAZAR',
    'CONTRATOS_EDITAR',
    'CONTRATOS_ANULAR',
    'CONTRATOS_FIRMAS_GESTIONAR'
)
WHERE r.codigo = 'ADMINISTRADOR'
  AND r.activo = 1
  AND p.activo = 1
ON DUPLICATE KEY UPDATE
    concedido = 1,
    actualizado_en = CURRENT_TIMESTAMP(6);

INSERT INTO rol_permisos (rol_id, permiso_id, concedido)
SELECT r.id, p.id, 1
FROM roles r
JOIN permisos p ON p.codigo = 'RESERVAS_CONVERTIR_CONTRATO'
WHERE r.codigo = 'COMERCIAL'
  AND r.activo = 1
  AND p.activo = 1
ON DUPLICATE KEY UPDATE
    concedido = 1,
    actualizado_en = CURRENT_TIMESTAMP(6);

INSERT INTO rol_permisos (rol_id, permiso_id, concedido)
SELECT r.id, p.id, 1
FROM roles r
JOIN permisos p ON p.activo = 1
WHERE r.codigo = 'SUPER_ADMIN'
  AND r.empresa_id IS NULL
  AND r.activo = 1
ON DUPLICATE KEY UPDATE
    concedido = 1,
    actualizado_en = CURRENT_TIMESTAMP(6);
