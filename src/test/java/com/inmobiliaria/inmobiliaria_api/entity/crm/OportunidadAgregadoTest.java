package com.inmobiliaria.inmobiliaria_api.entity.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Reglas de la oportunidad como documento de MongoDB (rama 8). */
class OportunidadAgregadoTest {

    private static final LocalDateTime FECHA = LocalDateTime.parse("2026-10-29T10:00:00");

    @Test
    void losLotesDeInteresTienenTopeNoSeRepitenYSoloUnoQuedaSeleccionado() {
        Oportunidad oportunidad = nueva();
        for (long lote = 1; lote <= 10; lote++) {
            oportunidad.agregarLote(lote, lote * 100, (int) (11 - lote));
        }
        assertThatThrownBy(() -> oportunidad.agregarLote(11, 1100L, 1)).isInstanceOf(ReglaNegocioException.class);
        oportunidad.quitarLote(1000L);
        assertThatThrownBy(() -> oportunidad.agregarLote(12, 200L, 1)).isInstanceOf(ReglaNegocioException.class);

        oportunidad.seleccionarLote(300L, true);
        oportunidad.seleccionarLote(500L, true);

        assertThat(oportunidad.getLotesInteres()).hasSize(9);
        assertThat(oportunidad.getLotesInteres()).filteredOn(OportunidadLote::isSeleccionado)
                .extracting(OportunidadLote::getLoteId).containsExactly(500L);
        assertThat(oportunidad.getLotesInteres().get(0).getPrioridad()).isEqualTo(2);
    }

    @Test
    void unaActividadActualizaElSeguimientoYSeListaDeLaMasReciente() {
        Oportunidad oportunidad = nueva();
        oportunidad.agregarActividad(1, TipoActividadComercial.LLAMADA, "Interesado", "Primera llamada",
                FECHA.minusDays(2), "Enviar brochure", FECHA.plusDays(1));
        oportunidad.agregarActividad(2, TipoActividadComercial.VISITA, "Muy interesado", "Visita al lote",
                FECHA, null, null);

        assertThat(oportunidad.getActividades()).extracting(ActividadComercial::getId).containsExactly(2L, 1L);
        assertThat(oportunidad.getFechaUltimaActividad()).isEqualTo(FECHA);
        assertThat(oportunidad.getActividades().get(0).getOportunidadId()).isEqualTo(7L);
    }

    @Test
    void elClienteDeLaReservaSeVinculaUnaSolaVez() {
        Oportunidad oportunidad = nueva();
        oportunidad.validarReserva(40L);

        assertThat(oportunidad.aplicarClienteDeReserva(40L)).isTrue();
        assertThat(oportunidad.aplicarClienteDeReserva(40L)).isFalse();
        assertThatThrownBy(() -> oportunidad.validarReserva(41L)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> oportunidad.aplicarClienteDeReserva(41L)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void ganarYAnularLaVentaSonIdempotentesYQuedanEnElHistorial() {
        Oportunidad oportunidad = nueva();

        assertThat(oportunidad.aplicarVentaGanada(FECHA, 1, 9L, 300L)).isTrue();
        assertThat(oportunidad.aplicarVentaGanada(FECHA, 2, 9L, 300L)).isFalse();
        assertThat(oportunidad.getEstado()).isEqualTo(EstadoOportunidad.GANADA);
        assertThat(oportunidad.getEtapaActual()).isEqualTo(EtapaOportunidad.CIERRE);
        assertThat(oportunidad.getFechaCierre()).isEqualTo(FECHA);

        assertThat(oportunidad.aplicarAnulacionVenta(3, 9L, 300L)).isTrue();
        assertThat(oportunidad.aplicarAnulacionVenta(4, 9L, 300L)).isFalse();
        assertThat(oportunidad.getEstado()).isEqualTo(EstadoOportunidad.ABIERTA);
        assertThat(oportunidad.getEtapaActual()).isEqualTo(EtapaOportunidad.NEGOCIACION);
        assertThat(oportunidad.getHistorialEtapas()).extracting(OportunidadEtapaHistorial::getId).containsExactly(1L, 3L);
    }

    @Test
    void laFirmaMandaAunqueLaOportunidadSeHayaCerradoComoPerdida() {
        Oportunidad oportunidad = nueva();
        oportunidad.cerrarPerdida("Se fue con la competencia", FECHA.minusDays(1));

        assertThat(oportunidad.aplicarVentaGanada(FECHA, 1, 9L, 300L)).isTrue();

        assertThat(oportunidad.getEstado()).isEqualTo(EstadoOportunidad.GANADA);
        assertThat(oportunidad.getMotivoPerdida()).isNull();
    }

    @Test
    void elProspectoSeConvierteUnaVezYNoParaOtroCliente() {
        Prospecto prospecto = new Prospecto(8L, 1L, 34L, "Ana Gómez", "3001234567", null, FuenteProspecto.WEB,
                new BigDecimal("150000000.00"), TemperaturaProspecto.TIBIO, null);

        assertThat(prospecto.aplicarVentaGanada(40L)).isTrue();
        assertThat(prospecto.aplicarVentaGanada(40L)).isFalse();
        assertThat(prospecto.getEstado()).isEqualTo(EstadoProspecto.CONVERTIDO);
        assertThatThrownBy(() -> prospecto.aplicarVentaGanada(41L)).isInstanceOf(ReglaNegocioException.class);

        prospecto.reabrirPorAnulacionContrato();
        assertThat(prospecto.getEstado()).isEqualTo(EstadoProspecto.CALIFICADO);
    }

    private static Oportunidad nueva() {
        Oportunidad oportunidad = new Oportunidad(8L, 1L, "OP-1", 5L, 34L, new BigDecimal("74000000.00"), null);
        oportunidad.identificar(7L);
        return oportunidad;
    }
}
