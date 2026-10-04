package com.inmobiliaria.inmobiliaria_api.dto.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConfiguracionCartera;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Periodicidad;
import java.math.BigDecimal;

public record ConfiguracionCarteraResponse(
        Long id,
        Long empresaId,
        BigDecimal tasaEfectivaAnual,
        BigDecimal tasaMoraEfectivaAnual,
        Periodicidad periodicidad,
        int diasPrimeraCuota,
        boolean autoGenerarPlan,
        Long version
) {
    public static ConfiguracionCarteraResponse from(ConfiguracionCartera configuracion) {
        return new ConfiguracionCarteraResponse(
                configuracion.getId(),
                configuracion.getEmpresaId(),
                configuracion.getTasaEfectivaAnual(),
                configuracion.getTasaMoraEfectivaAnual(),
                configuracion.getPeriodicidad(),
                configuracion.getDiasPrimeraCuota(),
                configuracion.isAutoGenerarPlan(),
                configuracion.getVersion()
        );
    }
}
