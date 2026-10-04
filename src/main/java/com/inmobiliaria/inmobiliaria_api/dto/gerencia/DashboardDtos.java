package com.inmobiliaria.inmobiliaria_api.dto.gerencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() { }

    public record Serie(String categoria, BigDecimal valor, long cantidad) { }

    public record ResumenGerencial(LocalDate desde, LocalDate hasta, Long proyectoId,
                                   BigDecimal ventas, long contratos,
                                   BigDecimal recaudo, BigDecimal carteraTotal,
                                   BigDecimal carteraVencida, BigDecimal indiceMora,
                                   BigDecimal costos, BigDecimal utilidad,
                                   BigDecimal margen, long lotesDisponibles,
                                   BigDecimal inventarioDisponible,
                                   long oportunidadesAbiertas, long tareasVencidas,
                                   long pqrsAbiertas) { }

    public record ResumenComercial(LocalDate desde, LocalDate hasta, Long proyectoId,
                                   long oportunidadesNuevas, long oportunidadesGanadas,
                                   long oportunidadesPerdidas, BigDecimal valorEmbudo,
                                   BigDecimal tasaConversion, long reservasActivas,
                                   List<Serie> embudo, List<Serie> ventasPorAsesor) { }

    public record ResumenCartera(LocalDate corte, Long proyectoId,
                                 BigDecimal saldoTotal, BigDecimal saldoVencido,
                                 BigDecimal recaudoMes, BigDecimal indiceMora,
                                 long cuotasVencidas, long promesasPendientes,
                                 List<Serie> edadesMora) { }

    public record ResumenServicio(LocalDate desde, LocalDate hasta, Long proyectoId,
                                  long solicitudesAbiertas, long solicitudesVencidas,
                                  BigDecimal horasPrimeraRespuesta,
                                  BigDecimal satisfaccionPromedio,
                                  long citasPendientes, long ticketsAbiertos,
                                  List<Serie> solicitudesPorTipo) { }
}
