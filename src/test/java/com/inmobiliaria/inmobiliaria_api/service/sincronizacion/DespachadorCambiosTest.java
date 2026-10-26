package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import org.mockito.InOrder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DespachadorCambiosTest {

    private final AlmacenTokenReanudacion tokens = mock(AlmacenTokenReanudacion.class);
    private final ProyectorLectura proyectorSolicitudes = proyector("solicitudes");
    private final ProyectorLectura proyectorTickets = proyector("tickets");

    @Test
    void enviaElCambioSoloAlProyectorDeSuColeccion() {
        DespachadorCambios despachador =
                new DespachadorCambios(List.of(proyectorSolicitudes, proyectorTickets), tokens);
        CambioDocumento cambio = cambio("solicitudes", "token-1");

        despachador.procesar(cambio);

        verify(proyectorSolicitudes).aplicar(cambio);
        verify(proyectorTickets, never()).aplicar(any());
    }

    private static ProyectorLectura proyector(String coleccion) {
        ProyectorLectura proyector = mock(ProyectorLectura.class);
        when(proyector.coleccion()).thenReturn(coleccion);
        return proyector;
    }

    private static CambioDocumento cambio(String coleccion, String token) {
        return new CambioDocumento(
                coleccion, TipoCambio.INSERCION, 15L, Map.of("estado", "ABIERTA"), token);
    }
    @Test
    void guardaElTokenDespuesDeAplicarElCambio() {
        DespachadorCambios despachador =
                new DespachadorCambios(List.of(proyectorSolicitudes), tokens);
        CambioDocumento cambio = cambio("solicitudes", "token-7");

        despachador.procesar(cambio);

        InOrder orden = inOrder(proyectorSolicitudes, tokens);
        orden.verify(proyectorSolicitudes).aplicar(cambio);
        orden.verify(tokens).guardar(DespachadorCambios.FLUJO, "token-7");
    }
    @Test
    void siElProyectorFallaNoAvanzaElTokenYPropagaElError() {
        DespachadorCambios despachador =
                new DespachadorCambios(List.of(proyectorSolicitudes), tokens);
        CambioDocumento cambio = cambio("solicitudes", "token-8");
        IllegalStateException falloMySql = new IllegalStateException("MySQL no responde");
        doThrow(falloMySql).when(proyectorSolicitudes).aplicar(cambio);

        assertThatThrownBy(() -> despachador.procesar(cambio)).isSameAs(falloMySql);

        verify(tokens, never()).guardar(any(), any());
    }
    @Test
    void unCambioDeUnaColeccionSinProyectorSoloAvanzaElToken() {
        DespachadorCambios despachador =
                new DespachadorCambios(List.of(proyectorSolicitudes), tokens);
        CambioDocumento cambio = cambio("contadores", "token-9");

        despachador.procesar(cambio);

        verify(proyectorSolicitudes, never()).aplicar(any());
        verify(tokens).guardar(DespachadorCambios.FLUJO, "token-9");
    }
}