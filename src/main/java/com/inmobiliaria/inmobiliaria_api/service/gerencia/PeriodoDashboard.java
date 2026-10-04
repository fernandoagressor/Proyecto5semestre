package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDate;

/**
 * Normaliza el rango temporal utilizado por los indicadores transaccionales.
 *
 * <p>Cuando el cliente no envía una fecha inicial, el dashboard consulta el
 * histórico completo del sistema. El límite de 1900 es compatible con MySQL
 * DATE y evita que el cambio de mes o de año oculte registros existentes.</p>
 */
record PeriodoDashboard(LocalDate desde, LocalDate hasta) {
    static final LocalDate INICIO_HISTORICO = LocalDate.of(1900, 1, 1);

    static PeriodoDashboard resolver(LocalDate desde, LocalDate hasta, LocalDate hoy) {
        LocalDate fin = hasta == null ? hoy : hasta;
        LocalDate inicio = desde == null ? INICIO_HISTORICO : desde;
        if (fin.isBefore(inicio)) {
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial");
        }
        return new PeriodoDashboard(inicio, fin);
    }
}
