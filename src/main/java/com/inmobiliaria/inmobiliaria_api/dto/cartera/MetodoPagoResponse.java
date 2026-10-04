package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.MetodoPago;

public record MetodoPagoResponse(
        Long id,
        String codigo,
        String nombre,
        boolean requiereReferencia,
        boolean requiereComprobante
) {
    public static MetodoPagoResponse from(MetodoPago metodo) {
        return new MetodoPagoResponse(
                metodo.getId(),
                metodo.getCodigo(),
                metodo.getNombre(),
                metodo.isRequiereReferencia(),
                metodo.isRequiereComprobante()
        );
    }
}
