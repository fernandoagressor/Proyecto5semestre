package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConfiguracionCartera;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.Cuota;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.PlanPago;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoPagoReserva;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.Reserva;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.CuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.PlanPagoRepository;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.reserva.ReservaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La parte de la cuota inicial que no cubrió la reserva se cobra como cuota 0 del plan. */
class PlanPagoCuotaInicialTest {

    private static final ContextoUsuario SESION = new ContextoUsuario(9L, 3L);
    private static final LocalDate FIRMA = LocalDate.of(2026, 11, 5);

    private final PlanPagoRepository planes = mock(PlanPagoRepository.class);
    private final CuotaRepository cuotas = mock(CuotaRepository.class);
    private final ConfiguracionCarteraService configuracion = mock(ConfiguracionCarteraService.class);
    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final PlanPagoService service = new PlanPagoService(planes, cuotas, mock(CarteraAlcanceService.class),
            new CuotaCalculadora(), configuracion, reservas);

    @BeforeEach
    void preparar() {
        when(configuracion.obtenerEntidad(3L)).thenReturn(new ConfiguracionCartera(3L));
        when(planes.saveAndFlush(any(PlanPago.class))).thenAnswer(i -> i.getArgument(0));
        when(planes.findByContratoIdAndEmpresaIdAndEstado(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void loQueFaltaDeLaCuotaInicialQuedaComoCuotaCero() {
        Contrato contrato = contrato("20000000", "80000000", 12);
        reserva("2000000", EstadoPagoReserva.CONFIRMADO);

        service.generarAutomatico(SESION, contrato);

        ArgumentCaptor<Cuota> inicial = ArgumentCaptor.forClass(Cuota.class);
        verify(cuotas).save(inicial.capture());
        assertThat(inicial.getValue().getNumero()).isZero();
        assertThat(inicial.getValue().getCapital()).isEqualByComparingTo("18000000");
        assertThat(inicial.getValue().getFechaVencimiento()).isEqualTo(FIRMA);
        ArgumentCaptor<List<Cuota>> regulares = ArgumentCaptor.forClass(List.class);
        verify(cuotas).saveAll(regulares.capture());
        assertThat(regulares.getValue()).hasSize(12).allMatch(c -> c.getNumero() >= 1);
    }

    @Test
    void siLaReservaCubreLaCuotaInicialNoHayCuotaCero() {
        Contrato contrato = contrato("2000000", "98000000", 24);
        reserva("2000000", EstadoPagoReserva.CONFIRMADO);

        service.generarAutomatico(SESION, contrato);

        verify(cuotas, never()).save(any(Cuota.class));
        verify(cuotas).saveAll(anyList());
    }

    @Test
    void ventaDeContadoConInicialPendienteSoloTieneLaCuotaCero() {
        Contrato contrato = contrato("100000000", "0", 1);
        reserva("2000000", EstadoPagoReserva.CONFIRMADO);

        service.generarAutomatico(SESION, contrato);

        ArgumentCaptor<Cuota> inicial = ArgumentCaptor.forClass(Cuota.class);
        verify(cuotas).save(inicial.capture());
        assertThat(inicial.getValue().getCapital()).isEqualByComparingTo("98000000");
        verify(cuotas, never()).saveAll(anyList());
    }

    @Test
    void unaReservaSinPagoConfirmadoNoAbonaALaCuotaInicial() {
        Contrato contrato = contrato("20000000", "80000000", 12);
        reserva("2000000", EstadoPagoReserva.PENDIENTE);

        assertThat(service.inicialPendiente(contrato)).isEqualByComparingTo("20000000");
    }

    private Contrato contrato(String cuotaInicial, String saldo, int numeroCuotas) {
        Contrato contrato = mock(Contrato.class);
        when(contrato.getId()).thenReturn(50L);
        when(contrato.getEmpresaId()).thenReturn(3L);
        when(contrato.getReservaId()).thenReturn(40L);
        when(contrato.getCuotaInicial()).thenReturn(new BigDecimal(cuotaInicial));
        when(contrato.getSaldoFinanciado()).thenReturn(new BigDecimal(saldo));
        when(contrato.getNumeroCuotas()).thenReturn(numeroCuotas);
        when(contrato.getFechaFirma()).thenReturn(FIRMA);
        when(contrato.getFechaInicio()).thenReturn(FIRMA);
        return contrato;
    }

    private void reserva(String valor, EstadoPagoReserva estadoPago) {
        Reserva reserva = mock(Reserva.class);
        when(reserva.getValorReserva()).thenReturn(new BigDecimal(valor));
        when(reserva.getEstadoPago()).thenReturn(estadoPago);
        when(reservas.findByIdAndEmpresaId(40L, 3L)).thenReturn(Optional.of(reserva));
    }
}
