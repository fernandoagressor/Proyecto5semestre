package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitaComercialTest {

    @Test
    void reprogramaYExigeResultadoAlFinalizar() {
        LocalDateTime inicio = LocalDateTime.now().plusDays(1);
        CitaComercial cita = new CitaComercial(
                1L,
                2L,
                "VISITA",
                inicio,
                inicio.plusHours(1),
                "Sala de ventas"
        );

        cita.reprogramar(
                inicio.plusDays(1),
                inicio.plusDays(1).plusHours(1),
                "Proyecto"
        );

        assertThat(cita.getEstado())
                .isEqualTo(EstadoCitaComercial.REPROGRAMADA);
        assertThatThrownBy(() -> cita.cambiarEstado(
                EstadoCitaComercial.REALIZADA,
                null
        )).isInstanceOf(ReglaNegocioException.class);
    }
}
