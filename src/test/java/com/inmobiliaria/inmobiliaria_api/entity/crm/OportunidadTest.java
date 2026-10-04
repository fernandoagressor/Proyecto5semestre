package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OportunidadTest {

    @Test
    void cambiaEtapaYProbabilidad() {
        Oportunidad oportunidad = oportunidad();

        EtapaOportunidad anterior = oportunidad.cambiarEtapa(
                EtapaOportunidad.PROPUESTA,
                null
        );

        assertThat(anterior).isEqualTo(EtapaOportunidad.PROSPECTO_RECIBIDO);
        assertThat(oportunidad.getEtapaActual())
                .isEqualTo(EtapaOportunidad.PROPUESTA);
        assertThat(oportunidad.getProbabilidadCierre())
                .isEqualByComparingTo("70");
    }

    @Test
    void cierrePerdidoExigeMotivoYBloqueaEdicion() {
        Oportunidad oportunidad = oportunidad();

        assertThatThrownBy(
                () -> oportunidad.cerrarPerdida(null, LocalDateTime.now())
        ).isInstanceOf(ReglaNegocioException.class);

        oportunidad.cerrarPerdida(
                "Eligió otro proyecto",
                LocalDateTime.now()
        );

        assertThat(oportunidad.getEstado())
                .isEqualTo(EstadoOportunidad.PERDIDA);
        assertThatThrownBy(() -> oportunidad.actualizarDatos(
                BigDecimal.TEN,
                null
        )).isInstanceOf(ReglaNegocioException.class);
    }

    private Oportunidad oportunidad() {
        return new Oportunidad(
                1L,
                2L,
                "OP-ABC123",
                3L,
                4L,
                new BigDecimal("120000000.00"),
                LocalDateTime.now().plusDays(1)
        );
    }
}
