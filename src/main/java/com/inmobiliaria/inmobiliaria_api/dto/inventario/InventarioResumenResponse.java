package com.inmobiliaria.inmobiliaria_api.dto.inventario;

import java.math.BigDecimal;

public record InventarioResumenResponse(
        long total,
        long disponibles,
        long reservados,
        long vendidos,
        long bloqueados,
        long pendientes,
        BigDecimal valorDisponible
) {
}
