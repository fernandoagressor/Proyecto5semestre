package com.inmobiliaria.inmobiliaria_api.controller.ventas.reserva;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoCondicionesRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.contrato.ContratoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaMotivoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaPagoRequest;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.ventas.reserva.ReservaVencimientoRequest;
import com.inmobiliaria.inmobiliaria_api.entity.ventas.reserva.EstadoReserva;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.ventas.contrato.ContratoService;
import com.inmobiliaria.inmobiliaria_api.service.ventas.reserva.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservas")
public class ReservaController {

    private final ReservaService reservaService;
    private final ContratoService contratoService;

    public ReservaController(
            ReservaService reservaService,
            ContratoService contratoService
    ) {
        this.reservaService = reservaService;
        this.contratoService = contratoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RESERVAS_VER')")
    public PaginaResponse<ReservaResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long loteId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return reservaService.listar(
                ContextoUsuario.empresa(jwt),
                estado,
                proyectoId,
                clienteId,
                loteId,
                pagina,
                tamano
        );
    }

    @GetMapping("/{reservaId}")
    @PreAuthorize("hasAuthority('RESERVAS_VER')")
    public ReservaResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservaId
    ) {
        return reservaService.obtener(
                ContextoUsuario.empresa(jwt),
                reservaId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESERVAS_CREAR')")
    public ReservaResponse crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReservaCrearRequest request
    ) {
        return reservaService.crear(
                ContextoUsuario.empresa(jwt),
                request
        );
    }

    @PatchMapping("/{reservaId}/vencimiento")
    @PreAuthorize("hasAuthority('RESERVAS_EDITAR')")
    public ReservaResponse extender(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservaId,
            @Valid @RequestBody ReservaVencimientoRequest request
    ) {
        return reservaService.extender(
                ContextoUsuario.empresa(jwt),
                reservaId,
                request.fechaVencimiento()
        );
    }

    @PatchMapping("/{reservaId}/pago/confirmar")
    @PreAuthorize("hasAuthority('PAGOS_CONFIRMAR')")
    public ReservaResponse confirmarPago(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservaId,
            @RequestBody(required = false) ReservaPagoRequest request
    ) {
        return reservaService.confirmarPago(
                ContextoUsuario.empresa(jwt),
                reservaId,
                request == null ? null : request.comprobanteArchivoId()
        );
    }

    @PatchMapping("/{reservaId}/pago/rechazar")
    @PreAuthorize("hasAuthority('PAGOS_RECHAZAR')")
    public ReservaResponse rechazarPago(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservaId,
            @Valid @RequestBody ReservaMotivoRequest request
    ) {
        return reservaService.rechazarPago(
                ContextoUsuario.empresa(jwt),
                reservaId,
                request.motivo()
        );
    }

    @PatchMapping("/{reservaId}/cancelar")
    @PreAuthorize("hasAuthority('RESERVAS_CANCELAR')")
    public ReservaResponse cancelar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservaId,
            @Valid @RequestBody ReservaMotivoRequest request
    ) {
        return reservaService.cancelar(
                ContextoUsuario.empresa(jwt),
                reservaId,
                request.motivo()
        );
    }

    @PostMapping("/{reservaId}/contrato")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESERVAS_CONVERTIR_CONTRATO')")
    public ContratoResponse convertir(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservaId,
            @Valid @RequestBody ContratoCondicionesRequest request
    ) {
        return contratoService.convertirReserva(
                ContextoUsuario.empresa(jwt),
                reservaId,
                request
        );
    }
}
