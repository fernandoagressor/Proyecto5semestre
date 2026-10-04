package com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContratoFirmanteTest {

    @Test
    void firmaValidaCompletaAlFirmante() {
        ContratoFirmante firmante = new ContratoFirmante(
                1L,
                2L,
                TipoFirmante.COMPRADOR,
                1,
                null
        );
        LocalDateTime fecha = LocalDateTime.of(2026, 8, 1, 15, 0);

        firmante.marcarEnviada(fecha.minusHours(2));
        firmante.registrarFirma(
                fecha,
                10L,
                EstadoValidacionFirma.VALIDA
        );

        assertEquals(EstadoFirmante.FIRMADA, firmante.getEstado());
        assertEquals(fecha, firmante.getFechaFirma());
        assertEquals(10L, firmante.getArchivoFirmaId());
    }
}
