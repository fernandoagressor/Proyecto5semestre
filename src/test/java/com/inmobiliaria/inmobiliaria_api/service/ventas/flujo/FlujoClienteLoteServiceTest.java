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
    @Mock
    private com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository loteRepository;
    @Mock
    private com.inmobiliaria.inmobiliaria_api.service.portal.PortalAccesoService portalAccesoService;
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
        when(prospectoService.crearRegistroTecnico(eq(contexto), any())).thenReturn(prospecto);
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
                "TRANSFERENCIA",
                null
        );
    }

    @Test
    void reservaConUnClienteQueYaExisteSinCrearloDeNuevo() {
        ClienteResponse cliente = mock(ClienteResponse.class);
        ProspectoResponse prospecto = mock(ProspectoResponse.class);
        OportunidadResponse oportunidad = mock(OportunidadResponse.class);
        when(cliente.id()).thenReturn(30L);
        when(cliente.estado()).thenReturn("ACTIVO");
        when(cliente.nombreCompleto()).thenReturn("Ana Pérez");
        when(cliente.correo()).thenReturn("ana@ejemplo.com");
        when(prospecto.id()).thenReturn(40L);
        when(oportunidad.id()).thenReturn(50L);
        when(clienteService.obtener(contexto, 30L)).thenReturn(cliente);
        when(prospectoService.crearRegistroTecnico(eq(contexto), any())).thenReturn(prospecto);
        when(prospectoService.calificar(eq(contexto), eq(40L), any())).thenReturn(prospecto);
        when(oportunidadService.crear(eq(contexto), any())).thenReturn(oportunidad);

        flujoService.crear(contexto, new FlujoClienteLoteRequest(null, 60L, 70L,
                LocalDateTime.now().plusDays(2), new BigDecimal("500000.00"), "TRANSFERENCIA", 30L));

        verify(clienteService, never()).crear(any(), any());
        verify(reservaService).crear(eq(contexto), any());
    }

    @Test
    void reutilizaElClienteSiSuDocumentoYaEstaRegistrado() {
        ClienteResponse cliente = mock(ClienteResponse.class);
        ProspectoResponse prospecto = mock(ProspectoResponse.class);
        OportunidadResponse oportunidad = mock(OportunidadResponse.class);
        when(cliente.id()).thenReturn(30L);
        when(cliente.nombreCompleto()).thenReturn("Ana Pérez");
        when(cliente.telefono()).thenReturn("3001234567");
        when(prospecto.id()).thenReturn(40L);
        when(oportunidad.id()).thenReturn(50L);
        when(clienteService.buscarPorDocumento(contexto, "CC", "100200300")).thenReturn(java.util.Optional.of(cliente));
        when(prospectoService.crearRegistroTecnico(eq(contexto), any())).thenReturn(prospecto);
        when(prospectoService.calificar(eq(contexto), eq(40L), any())).thenReturn(prospecto);
        when(oportunidadService.crear(eq(contexto), any())).thenReturn(oportunidad);

        flujoService.crear(contexto, request(new ClienteRequest(
                "NATURAL", "CC", "100200300", "Ana", "Pérez", "3001234567", null, null, "VENTAS", null)));

        verify(clienteService, never()).crear(any(), any());
    }

    @Test
    void unLoteYaReservadoSeRechazaAntesDeCrearNada() {
        com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote lote =
                com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote.pendiente(20L, 60L, 1L, 1L, "E01-M01-L01", "1");
        when(loteRepository.findByIdAndEmpresaIdAndProyectoId(70L, 20L, 60L)).thenReturn(java.util.Optional.of(lote));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> flujoService.crear(contexto,
                new FlujoClienteLoteRequest(null, 60L, 70L, LocalDateTime.now().plusDays(2),
                        new BigDecimal("500000.00"), "TRANSFERENCIA", 30L)));

        org.assertj.core.api.Assertions.assertThat(error.getMessage()).contains("medidas y precio");
        verify(clienteService, never()).obtener(any(), any());
        verify(prospectoService, never()).crearRegistroTecnico(any(), any());
    }

    @Test
    void sinClienteElegidoNiNuevoNoReserva() {
        assertThrows(ReglaNegocioException.class, () -> flujoService.crear(contexto, new FlujoClienteLoteRequest(
                null, 60L, 70L, LocalDateTime.now().plusDays(2), new BigDecimal("500000.00"), "TRANSFERENCIA", null)));
        verify(reservaService, never()).crear(any(), any());
    }

    @Test
    void reservaSobreElProspectoQueYaTieneElCrmSinDuplicarlo() {
        clienteActivo();
        ProspectoResponse existente = mock(ProspectoResponse.class);
        OportunidadResponse oportunidad = mock(OportunidadResponse.class);
        when(existente.id()).thenReturn(41L);
        when(existente.proyectoId()).thenReturn(60L);
        when(existente.estado()).thenReturn(com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto.CALIFICADO);
        when(oportunidad.id()).thenReturn(50L);
        when(prospectoService.buscarActivosPorContacto(contexto, "3001234567", null))
                .thenReturn(java.util.List.of(existente));
        when(oportunidadService.tieneAbierta(41L)).thenReturn(false);
        when(oportunidadService.crear(eq(contexto), any())).thenReturn(oportunidad);

        flujoService.crear(contexto, new FlujoClienteLoteRequest(null, 60L, 70L,
                LocalDateTime.now().plusDays(2), new BigDecimal("500000.00"), "TRANSFERENCIA", 30L));

        verify(prospectoService, never()).crearRegistroTecnico(any(), any());
        verify(prospectoService, never()).calificar(any(), any(), any());
        verify(oportunidadService).crear(eq(contexto),
                org.mockito.ArgumentMatchers.argThat(o -> o.prospectoId().equals(41L)));
        verify(reservaService).crear(eq(contexto), any());
    }

    @Test
    void unClienteQueYaReservoOtroLotePuedeReservarOtro() {
        clienteActivo();
        ProspectoResponse enOtraVenta = mock(ProspectoResponse.class);
        ProspectoResponse nuevo = mock(ProspectoResponse.class);
        OportunidadResponse oportunidad = mock(OportunidadResponse.class);
        when(enOtraVenta.id()).thenReturn(41L);
        when(enOtraVenta.proyectoId()).thenReturn(60L);
        when(nuevo.id()).thenReturn(42L);
        when(oportunidad.id()).thenReturn(50L);
        when(prospectoService.buscarActivosPorContacto(contexto, "3001234567", null))
                .thenReturn(java.util.List.of(enOtraVenta));
        when(oportunidadService.tieneAbierta(41L)).thenReturn(true);
        when(prospectoService.crearRegistroTecnico(eq(contexto), any())).thenReturn(nuevo);
        when(prospectoService.calificar(eq(contexto), eq(42L), any())).thenReturn(nuevo);
        when(oportunidadService.crear(eq(contexto), any())).thenReturn(oportunidad);

        flujoService.crear(contexto, new FlujoClienteLoteRequest(null, 60L, 70L,
                LocalDateTime.now().plusDays(2), new BigDecimal("500000.00"), "TRANSFERENCIA", 30L));

        verify(prospectoService).crearRegistroTecnico(eq(contexto), any());
        verify(reservaService).crear(eq(contexto), any());
    }

    private ClienteResponse clienteActivo() {
        ClienteResponse cliente = mock(ClienteResponse.class);
        when(cliente.id()).thenReturn(30L);
        when(cliente.estado()).thenReturn("ACTIVO");
        when(cliente.telefono()).thenReturn("3001234567");
        when(clienteService.obtener(contexto, 30L)).thenReturn(cliente);
        return cliente;
    }

    @Test
    void alReservarConUnClienteNuevoSeLeCreaElAccesoAlPortal() {
        ClienteResponse cliente = mock(ClienteResponse.class);
        ProspectoResponse prospecto = mock(ProspectoResponse.class);
        OportunidadResponse oportunidad = mock(OportunidadResponse.class);
        when(cliente.id()).thenReturn(30L);
        when(cliente.telefono()).thenReturn("3001234567");
        when(prospecto.id()).thenReturn(40L);
        when(oportunidad.id()).thenReturn(50L);
        when(clienteService.crear(eq(contexto), any())).thenReturn(cliente);
        when(prospectoService.crearRegistroTecnico(eq(contexto), any())).thenReturn(prospecto);
        when(prospectoService.calificar(eq(contexto), eq(40L), any())).thenReturn(prospecto);
        when(oportunidadService.crear(eq(contexto), any())).thenReturn(oportunidad);
        var acceso = new com.inmobiliaria.inmobiliaria_api.dto.portal.PortalDtos.Acceso(
                30L, 90L, "100200300", null, "Temporal-123!");
        when(portalAccesoService.habilitarSiFalta(contexto, 30L)).thenReturn(acceso);
        when(cliente.usuarioId()).thenReturn(null); // cliente nuevo: todavía sin cuenta del portal

        FlujoClienteLoteResponse result = flujoService.crear(contexto, request(new ClienteRequest(
                "NATURAL", "CC", "100200300", "Ana", "Pérez", "3001234567", null, null, "VENTAS", null)), true);

        assertEquals(acceso, result.accesoPortal());
        assertEquals(false, result.portalExistente());
    }

    @Test
    void sinPermisoDePortalNoSeCreaElAcceso() {
        ClienteResponse cliente = mock(ClienteResponse.class);
        ProspectoResponse prospecto = mock(ProspectoResponse.class);
        OportunidadResponse oportunidad = mock(OportunidadResponse.class);
        when(cliente.id()).thenReturn(30L);
        when(cliente.telefono()).thenReturn("3001234567");
        when(prospecto.id()).thenReturn(40L);
        when(oportunidad.id()).thenReturn(50L);
        when(clienteService.crear(eq(contexto), any())).thenReturn(cliente);
        when(prospectoService.crearRegistroTecnico(eq(contexto), any())).thenReturn(prospecto);
        when(prospectoService.calificar(eq(contexto), eq(40L), any())).thenReturn(prospecto);
        when(oportunidadService.crear(eq(contexto), any())).thenReturn(oportunidad);

        FlujoClienteLoteResponse result = flujoService.crear(contexto, request(new ClienteRequest(
                "NATURAL", "CC", "100200300", "Ana", "Pérez", "3001234567", null, null, "VENTAS", null)), false);

        assertEquals(null, result.accesoPortal());
        verify(portalAccesoService, never()).habilitarSiFalta(any(), any());
    }
}
