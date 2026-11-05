package com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cómo quedaría el contrato y su plan de pagos con estas condiciones, sin guardar nada.
 * {@code inicialPendiente} es la cuota 0 que cobrará Cartera (cuota inicial menos lo pagado en
 * la reserva) y {@code cuotas} es el cronograma que generará al activarse la venta.
 */
public record ContratoVistaPreviaResponse(
        BigDecimal valorLote,
        BigDecimal otrosConceptos,
        BigDecimal descuento,
        BigDecimal valorTotal,
        BigDecimal cuotaInicial,
        BigDecimal abonadoEnReserva,
        BigDecimal inicialPendiente,
        BigDecimal saldoFinanciado,
        BigDecimal tasaEfectivaAnual,
        String periodicidad,
        boolean planAutomatico,
        boolean admiteDescuento,
        BigDecimal valorCuota,
        LocalDate primeraCuota,
        LocalDate ultimaCuota,
        List<CuotaProyectada> cuotas
) {
    public record CuotaProyectada(int numero, LocalDate fechaVencimiento, BigDecimal valor) {
    }
}
