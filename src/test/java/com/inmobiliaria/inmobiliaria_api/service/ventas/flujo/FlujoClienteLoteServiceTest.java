package com.inmobiliaria.inmobiliaria_api.service.ventas.flujo;

import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ProspectoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo.FlujoClienteLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.flujo.FlujoClienteLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cliente.ClienteService;
import com.inmobiliaria.inmobiliaria_api.service.crm.OportunidadService;
import com.inmobiliaria.inmobiliaria_api.service.crm.ProspectoService;
import com.inmobiliaria.inmobiliaria_api.service.ventas.reserva.ReservaService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlujoClienteLoteServiceTest {

    @Mock
    private ClienteService clienteService;
    @Mock
    private ProspectoService prospectoService;
    @Mock
    private OportunidadService oportunidadService;
    @Mock
    private ReservaService reservaService;
    @InjectMocks
    private FlujoClienteLoteService flujoService;

    private final ContextoUsuario contexto = new ContextoUsuario(10L, 20L);

    @Test
    void exigeUnCanalDeContactoAntesDeCrearRegistros() {
        FlujoClienteLoteRequest request = request(new ClienteRequest(
                "NATURAL", "CC", "100200300", "Ana", "Pérez",
                null, null, null, "VENTAS", null
        ));

        assertThrows(
                ReglaNegocioException.class,
                () -> flujoService.crear(contexto, request)
        );
        verify(clienteService, never()).crear(any(), any());
    }

    @Test
    void creaLaReservaConLaTrazabilidadInternaEnUnSoloFlujo() {
        ClienteResponse cliente = mock(ClienteResponse.class);
        ProspectoResponse prospecto = mock(ProspectoResponse.class);
        OportunidadResponse oportunidad = mock(OportunidadResponse.class);
        ReservaResponse reserva = mock(ReservaResponse.class);
        when(cliente.id()).thenReturn(30L);
        when(cliente.nombreCompleto()).thenReturn("Ana Pérez");
        when(cliente.telefono()).thenReturn("3001234567");
        when(prospecto.id()).thenReturn(40L);
        when(oportunidad.id()).thenReturn(50L);
        when(clienteService.crear(eq(contexto), any())).thenReturn(cliente);
        when(prospectoService.crear(eq(contexto), any())).thenReturn(prospecto);
        when(prospectoService.calificar(eq(contexto), eq(40L), any())).thenReturn(prospecto);
        when(oportunidadService.crear(eq(contexto), any())).thenReturn(oportunidad);
        when(reservaService.crear(eq(contexto), any())).thenReturn(reserva);

        FlujoClienteLoteResponse result = flujoService.crear(
                contexto,
                request(new ClienteRequest(
                        "NATURAL", "CC", "100200300", "Ana", "Pérez",
                        "3001234567", null, null, "VENTAS", null
                ))
        );

        assertEquals(cliente, result.cliente());
        assertEquals(oportunidad, result.oportunidad());
        assertEquals(reserva, result.reserva());
        verify(oportunidadService).agregarLote(contexto, 50L, 70L, 1);
        verify(oportunidadService).seleccionarLote(contexto, 50L, 70L, true);
        verify(reservaService).crear(eq(contexto), any());
    }

    private static FlujoClienteLoteRequest request(ClienteRequest cliente) {
        return new FlujoClienteLoteRequest(
                cliente,
                60L,
                70L,
                LocalDateTime.now().plusDays(2),
                new BigDecimal("500000.00"),
                "TRANSFERENCIA"
        );
    }
}
