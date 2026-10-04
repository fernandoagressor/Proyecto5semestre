package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.GestionCobroRequest;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.CanalCobro;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConceptoPago;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.EstadoCuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.GestionCobro;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.CuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.EstadoCuentaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.GestionCobroRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PazSalvoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PlanPagoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.RefinanciacionCuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.RefinanciacionRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarteraGestionServiceTest {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ContextoUsuario CONTEXTO = new ContextoUsuario(11L, 1L);

    @Mock private CuotaRepository cuotaRepository;
    @Mock private PlanPagoRepository planRepository;
    @Mock private GestionCobroRepository gestionRepository;
    @Mock private RefinanciacionRepository refinanciacionRepository;
    @Mock private RefinanciacionCuotaRepository refinanciacionCuotaRepository;
    @Mock private EstadoCuentaRepository estadoCuentaRepository;
    @Mock private PazSalvoRepository pazSalvoRepository;
    @Mock private CarteraAlcanceService alcanceService;
    @Mock private AlcanceProyectoService alcanceProyectoService;
    @Mock private PlanPagoService planPagoService;
    @Mock private CuotaCalculadora calculadora;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private CarteraGestionService service;

    @Test
    void registraGestionPreventivaEnCuotaParcial() {
        LocalDate hoy = LocalDate.now(BOGOTA);
        Cuota cuota = cuotaConVencimiento(hoy.plusMonths(1));
        cuota.aplicar(new BigDecimal("200000"), ConceptoPago.CAPITAL, hoy);
        when(cuotaRepository.findForUpdate(70L, 1L)).thenReturn(Optional.of(cuota));
        when(gestionRepository.save(any(GestionCobro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.registrarGestion(CONTEXTO, new GestionCobroRequest(
                7L,
                70L,
                CanalCobro.LLAMADA,
                "Contactado",
                "Seguimiento preventivo",
                hoy.plusDays(5),
                new BigDecimal("300000")
        ));

        assertEquals(EstadoCuota.PARCIAL, cuota.getEstado());
        assertEquals(7L, response.contratoId());
        assertEquals(70L, response.cuotaId());
        assertEquals(new BigDecimal("300000"), response.promesaPagoValor());
    }

    @Test
    void rechazaGestionSobreCuotaPagada() {
        LocalDate hoy = LocalDate.now(BOGOTA);
        Cuota cuota = cuotaConVencimiento(hoy.plusMonths(1));
        cuota.aplicar(new BigDecimal("1000000"), ConceptoPago.CAPITAL, hoy);
        when(cuotaRepository.findForUpdate(70L, 1L)).thenReturn(Optional.of(cuota));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () ->
                service.registrarGestion(CONTEXTO, new GestionCobroRequest(
                        7L, 70L, CanalCobro.EMAIL, "Contactado", null, null, null
                ))
        );

        assertEquals("La cuota no tiene saldo pendiente para gestionar", error.getMessage());
        verify(gestionRepository, never()).save(any());
    }

    @Test
    void rechazaPromesaSuperiorAlSaldoDeLaCuota() {
        LocalDate hoy = LocalDate.now(BOGOTA);
        Cuota cuota = cuotaConVencimiento(hoy.plusMonths(1));
        cuota.aplicar(new BigDecimal("200000"), ConceptoPago.CAPITAL, hoy);
        when(cuotaRepository.findForUpdate(70L, 1L)).thenReturn(Optional.of(cuota));

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () ->
                service.registrarGestion(CONTEXTO, new GestionCobroRequest(
                        7L,
                        70L,
                        CanalCobro.WHATSAPP,
                        "Promesa obtenida",
                        null,
                        hoy.plusDays(3),
                        new BigDecimal("900000")
                ))
        );

        assertEquals("La promesa supera el saldo pendiente de la cuota", error.getMessage());
        verify(gestionRepository, never()).save(any());
    }

    private static Cuota cuotaConVencimiento(LocalDate vencimiento) {
        return new Cuota(
                1L,
                2L,
                7L,
                4,
                vencimiento,
                new BigDecimal("1000000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
