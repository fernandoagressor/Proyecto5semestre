package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProspectoTest {

    @Test
    void exigeMotivoAlDescartar() {
        Prospecto prospecto = prospecto();

        assertThatThrownBy(() -> prospecto.cambiarCalificacion(
                EstadoProspecto.DESCARTADO,
                TemperaturaProspecto.FRIO,
                null
        )).isInstanceOf(ReglaNegocioException.class);

        prospecto.cambiarCalificacion(
                EstadoProspecto.DESCARTADO,
                TemperaturaProspecto.FRIO,
                "No cuenta con presupuesto"
        );

        assertThat(prospecto.getEstado())
                .isEqualTo(EstadoProspecto.DESCARTADO);
    }

    @Test
    void impideConversionManual() {
        Prospecto prospecto = prospecto();

        assertThatThrownBy(() -> prospecto.cambiarCalificacion(
                EstadoProspecto.CONVERTIDO,
                TemperaturaProspecto.CALIENTE,
                null
        )).isInstanceOf(ReglaNegocioException.class);
    }

    private Prospecto prospecto() {
        return new Prospecto(
                1L,
                2L,
                3L,
                "Laura Pérez",
                "3001234567",
                "laura@example.com",
                FuenteProspecto.WEB,
                new BigDecimal("150000000.00"),
                TemperaturaProspecto.TIBIO,
                null
        );
    }
}
