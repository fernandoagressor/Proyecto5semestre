package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import java.util.Map;

/**
 * Un cambio ocurrido en una colección de MongoDB, ya traducido a tipos simples.
 *
 * @param coleccion         colección donde ocurrió el cambio
 * @param tipo              inserción, actualización o eliminación
 * @param id                identificador numérico del documento (el mismo de la tabla espejo)
 * @param datos             campos del documento después del cambio (vacío si se eliminó)
 * @param tokenReanudacion  posición del cambio en el Change Stream, para poder continuar
 */
public record CambioDocumento(
        String coleccion,
        TipoCambio tipo,
        Long id,
        Map<String, Object> datos,
        String tokenReanudacion
) {
}