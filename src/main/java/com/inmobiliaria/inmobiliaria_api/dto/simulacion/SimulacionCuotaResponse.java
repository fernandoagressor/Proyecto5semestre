package com.inmobiliaria.inmobiliaria_api.dto.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.simulacion.SimulacionCuota;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SimulacionCuotaResponse(
        int numero,
        LocalDate fechaVencimiento,
        BigDecimal capital,
        BigDecimal interes,
        BigDecimal otros,
        BigDecimal valorTotal,
        BigDecimal saldoProyectado
) {
    public static SimulacionCuotaResponse from(SimulacionCuota cuota) {
        return new SimulacionCuotaResponse(
                cuota.getNumero(),
                cuota.getFechaVencimiento(),
                cuota.getCapital(),
                cuota.getInteres(),
                cuota.getOtros(),
                cuota.getValorTotal(),
                cuota.getSaldoProyectado()
        );
    }
}
