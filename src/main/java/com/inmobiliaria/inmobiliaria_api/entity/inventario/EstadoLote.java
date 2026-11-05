package com.inmobiliaria.inmobiliaria_api.entity.inventario;

public enum EstadoLote {
    /** Generado en bloque, todavía sin medidas o sin precio: no se puede reservar ni vender. */
    PENDIENTE,
    DISPONIBLE,
    RESERVADO,
    VENDIDO,
    BLOQUEADO
}
