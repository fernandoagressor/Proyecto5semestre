package com.inmobiliaria.inmobiliaria_api.controller.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.CarteraContratoResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.CarteraResumenResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConfiguracionCarteraRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConfiguracionCarteraResponse;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.MetodoPagoResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.cartera.CarteraOperativaService;
import com.inmobiliaria.inmobiliaria_api.service.cartera.ConfiguracionCarteraService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cartera")
public class CarteraOperativaController {
    private final CarteraOperativaService operativaService;
    private final ConfiguracionCarteraService configuracionService;

    public CarteraOperativaController(
            CarteraOperativaService operativaService,
            ConfiguracionCarteraService configuracionService
    ) {
        this.operativaService = operativaService;
        this.configuracionService = configuracionService;
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('CUOTAS_VER')")
    public CarteraResumenResponse resumen(@AuthenticationPrincipal Jwt jwt) {
        return operativaService.resumen(ContextoUsuario.empresa(jwt));
    }

    @GetMapping("/contratos")
    @PreAuthorize("hasAuthority('CUOTAS_VER')")
    public List<CarteraContratoResponse> contratos(@AuthenticationPrincipal Jwt jwt) {
        return operativaService.contratos(ContextoUsuario.empresa(jwt));
    }

    @GetMapping("/metodos-pago")
    @PreAuthorize("hasAuthority('PAGOS_CREAR')")
    public List<MetodoPagoResponse> metodosPago(@AuthenticationPrincipal Jwt jwt) {
        return operativaService.metodosPago(ContextoUsuario.empresa(jwt));
    }

    @GetMapping("/configuracion")
    @PreAuthorize("hasAuthority('CUOTAS_PROGRAMAR')")
    public ConfiguracionCarteraResponse configuracion(@AuthenticationPrincipal Jwt jwt) {
        return configuracionService.obtener(ContextoUsuario.empresa(jwt));
    }

    @PutMapping("/configuracion")
    @PreAuthorize("hasAuthority('CUOTAS_PROGRAMAR')")
    public ConfiguracionCarteraResponse actualizarConfiguracion(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfiguracionCarteraRequest request
    ) {
        return configuracionService.actualizar(ContextoUsuario.empresa(jwt), request);
    }
}
