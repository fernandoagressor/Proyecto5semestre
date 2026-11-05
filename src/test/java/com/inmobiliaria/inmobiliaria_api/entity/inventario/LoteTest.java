package com.inmobiliaria.inmobiliaria_api.entity.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoteTest {

    @Test
    void calculaAreaAutomaticamenteConFrenteYFondo() {
        Lote lote = new Lote(
                1L,
                2L,
                3L,
                4L,
                "A-02",
                "02",
                new BigDecimal("999.00"),
                new BigDecimal("8.25"),
                new BigDecimal("12.50"),
                new BigDecimal("120000000.00"),
                null,
                null
        );

        assertThat(lote.getAreaM2()).isEqualByComparingTo("103.13");
    }

    @Test
    void recalculaAreaCuandoCambianLasDimensiones() {
        Lote lote = lote();

        lote.actualizarDatos(
                "A-01",
                "01",
                new BigDecimal("999.00"),
                new BigDecimal("9.50"),
                new BigDecimal("14.00"),
                "Esquina",
                null
        );

        assertThat(lote.getAreaM2()).isEqualByComparingTo("133.00");
    }

    @Test
    void conservaAreaManualCuandoFaltaUnaDimension() {
        Lote lote = lote();

        lote.actualizarDatos(
                "A-01",
                "01",
                new BigDecimal("145.75"),
                new BigDecimal("8.00"),
                null,
                "Esquina",
                null
        );

        assertThat(lote.getAreaM2()).isEqualByComparingTo("145.75");
    }

    @Test
    void sincronizaDisponibilidadConCambiosManualesPermitidos() {
        Lote lote = lote();

        EstadoLote anterior = lote.cambiarEstadoManual(EstadoLote.BLOQUEADO);

        assertThat(anterior).isEqualTo(EstadoLote.DISPONIBLE);
        assertThat(lote.getEstado()).isEqualTo(EstadoLote.BLOQUEADO);
        assertThat(lote.isDisponible()).isFalse();

        lote.cambiarEstadoManual(EstadoLote.DISPONIBLE);

        assertThat(lote.isDisponible()).isTrue();
    }

    @Test
    void impideAsignarReservaOVentaPorCambioManual() {
        Lote lote = lote();

        assertThatThrownBy(
                () -> lote.cambiarEstadoManual(EstadoLote.RESERVADO)
        ).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(
                () -> lote.cambiarEstadoManual(EstadoLote.VENDIDO)
        ).isInstanceOf(ReglaNegocioException.class);

        assertThat(lote.getEstado()).isEqualTo(EstadoLote.DISPONIBLE);
    }

    @Test
    void cambiaPrecioYDevuelveValorAnterior() {
        Lote lote = lote();

        BigDecimal anterior = lote.cambiarPrecio(
                new BigDecimal("125000000.00")
        );

        assertThat(anterior).isEqualByComparingTo("120000000.00");
        assertThat(lote.getValorLista()).isEqualByComparingTo("125000000.00");
    }

    @Test
    void rechazaPrecioIgualONegativo() {
        Lote lote = lote();

        assertThatThrownBy(
                () -> lote.cambiarPrecio(new BigDecimal("120000000.00"))
        ).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(
                () -> lote.cambiarPrecio(new BigDecimal("-1"))
        ).isInstanceOf(ReglaNegocioException.class);
    }

    private Lote lote() {
        return new Lote(
                1L,
                2L,
                3L,
                4L,
                "A-01",
                "01",
                new BigDecimal("120.00"),
                new BigDecimal("8.00"),
                new BigDecimal("15.00"),
                new BigDecimal("120000000.00"),
                "Esquina",
                null
        );
    }

    @Test
    void unLoteGeneradoNacePendienteYQuedaDisponibleAlCompletarMedidasYPrecio() {
        Lote lote = Lote.pendiente(1L, 2L, 3L, 4L, "E01-M01-L01", "1");

        assertThat(lote.getEstado()).isEqualTo(EstadoLote.PENDIENTE);
        assertThat(lote.isDisponible()).isFalse();
        assertThat(lote.habilitarSiEstaCompleto()).isNull();

        lote.actualizarDatos("E01-M01-L01", "1", null, new BigDecimal("8"), new BigDecimal("15"), null, null);
        assertThat(lote.habilitarSiEstaCompleto()).as("sin precio sigue pendiente").isNull();

        lote.fijarPrecioInicial(new BigDecimal("54000000"));
        assertThat(lote.habilitarSiEstaCompleto()).isEqualTo(EstadoLote.PENDIENTE);
        assertThat(lote.getEstado()).isEqualTo(EstadoLote.DISPONIBLE);
        assertThat(lote.isDisponible()).isTrue();
        assertThat(lote.getAreaM2()).isEqualByComparingTo("120.00");
    }

    @Test
    void unLotePendienteNoSeReservaNiCambiaDeEstadoAMano() {
        Lote lote = Lote.pendiente(1L, 2L, 3L, 4L, "E01-M01-L02", "2");

        assertThatThrownBy(lote::reservar).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> lote.cambiarEstadoManual(EstadoLote.DISPONIBLE))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Complete las medidas y el precio");
    }

    @Test
    void unLoteDisponibleNoPuedeQuedarSinArea() {
        Lote lote = lote();

        assertThatThrownBy(() -> lote.actualizarDatos("A-01", "01", null, null, null, null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("área");
    }

    @Test
    void elPrimerPrecioDeUnLotePendienteNoExigeQueSeaDistinto() {
        Lote lote = Lote.pendiente(1L, 2L, 3L, 4L, "E01-M01-L03", "3");

        assertThat(lote.cambiarPrecio(new BigDecimal("0"))).isNull();
        assertThat(lote.getValorLista()).isEqualByComparingTo("0");
    }
}
