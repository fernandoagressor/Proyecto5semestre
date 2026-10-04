package com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContratoTest {

    @Test
    void derivaTotalYSaldoSinRecibirlosDelCliente() {
        Contrato contrato = contrato();

        assertEquals(new BigDecimal("202000000.00"), contrato.getValorTotal());
        assertEquals(new BigDecimal("162000000.00"), contrato.getSaldoFinanciado());
    }

    @Test
    void activaSoloDespuesDeEnviarAFirma() {
        Contrato contrato = contrato();

        assertThrows(
                ReglaNegocioException.class,
                () -> contrato.activar(LocalDate.of(2026, 8, 1))
        );
        contrato.enviarAFirma();
        contrato.activar(LocalDate.of(2026, 8, 1));

        assertEquals(EstadoContrato.VIGENTE, contrato.getEstado());
        assertEquals(LocalDate.of(2026, 8, 1), contrato.getFechaFirma());
    }

    @Test
    void anulaDeFormaTrazable() {
        Contrato contrato = contrato();
        LocalDateTime fecha = LocalDateTime.of(2026, 8, 2, 9, 30);

        contrato.anular("Desistimiento documentado", fecha, 9L);

        assertEquals(EstadoContrato.ANULADO, contrato.getEstado());
        assertEquals("Desistimiento documentado", contrato.getMotivoAnulacion());
        assertEquals(fecha, contrato.getAnuladoEn());
        assertEquals(9L, contrato.getAnuladoPor());
    }

    private static Contrato contrato() {
        return new Contrato(
                1L,
                2L,
                "CTR-TEST",
                3L,
                4L,
                5L,
                6L,
                7L,
                TipoContrato.PROMESA_COMPRAVENTA,
                new BigDecimal("200000000.00"),
                new BigDecimal("5000000.00"),
                new BigDecimal("3000000.00"),
                new BigDecimal("40000000.00"),
                36,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2029, 8, 1)
        );
    }
}
