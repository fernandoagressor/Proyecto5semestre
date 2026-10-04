package com.inmobiliaria.inmobiliaria_api.controller.inventario;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioEstadoLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.CambioPrecioLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.EtapaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.EtapaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.HistorialEstadoLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.HistorialPrecioLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.InventarioResumenResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.LoteActualizarRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.LoteCrearRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.LoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ManzanaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.inventario.ManzanaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.inventario.InventarioService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/proyectos/{proyectoId}")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping("/etapas")
    @PreAuthorize("hasAuthority('ETAPAS_VER')")
    public List<EtapaResponse> listarEtapas(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId
    ) {
        return inventarioService.listarEtapas(
                ContextoUsuario.empresa(jwt),
                proyectoId
        );
    }

    @PostMapping("/etapas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ETAPAS_GESTIONAR')")
    public EtapaResponse crearEtapa(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @Valid @RequestBody EtapaRequest request
    ) {
        return inventarioService.crearEtapa(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                request
        );
    }

    @PutMapping("/etapas/{etapaId}")
    @PreAuthorize("hasAuthority('ETAPAS_GESTIONAR')")
    public EtapaResponse actualizarEtapa(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long etapaId,
            @Valid @RequestBody EtapaRequest request
    ) {
        return inventarioService.actualizarEtapa(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                etapaId,
                request
        );
    }

    @GetMapping("/etapas/{etapaId}/manzanas")
    @PreAuthorize("hasAuthority('MANZANAS_VER')")
    public List<ManzanaResponse> listarManzanas(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long etapaId
    ) {
        return inventarioService.listarManzanas(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                etapaId
        );
    }

    @PostMapping("/etapas/{etapaId}/manzanas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANZANAS_GESTIONAR')")
    public ManzanaResponse crearManzana(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long etapaId,
            @Valid @RequestBody ManzanaRequest request
    ) {
        return inventarioService.crearManzana(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                etapaId,
                request
        );
    }

    @PutMapping("/etapas/{etapaId}/manzanas/{manzanaId}")
    @PreAuthorize("hasAuthority('MANZANAS_GESTIONAR')")
    public ManzanaResponse actualizarManzana(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long etapaId,
            @PathVariable Long manzanaId,
            @Valid @RequestBody ManzanaRequest request
    ) {
        return inventarioService.actualizarManzana(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                etapaId,
                manzanaId,
                request
        );
    }

    @GetMapping("/lotes")
    @PreAuthorize("""
            hasAuthority('LOTES_VER')
            or hasAuthority('LOTES_DISPONIBILIDAD_VER')
            """)
    public PaginaResponse<LoteResponse> listarLotes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @RequestParam(required = false) EstadoLote estado,
            @RequestParam(required = false) Long etapaId,
            @RequestParam(required = false) Long manzanaId,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) BigDecimal areaMinima,
            @RequestParam(required = false) BigDecimal areaMaxima,
            @RequestParam(required = false) BigDecimal valorMinimo,
            @RequestParam(required = false) BigDecimal valorMaximo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return inventarioService.listarLotes(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                estado,
                etapaId,
                manzanaId,
                texto,
                areaMinima,
                areaMaxima,
                valorMinimo,
                valorMaximo,
                pagina,
                tamano
        );
    }

    @GetMapping("/lotes/{loteId}")
    @PreAuthorize("hasAuthority('LOTES_VER')")
    public LoteResponse obtenerLote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long loteId
    ) {
        return inventarioService.obtenerLote(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                loteId
        );
    }

    @PostMapping("/lotes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LOTES_CREAR')")
    public LoteResponse crearLote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @Valid @RequestBody LoteCrearRequest request
    ) {
        return inventarioService.crearLote(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                request
        );
    }

    @PutMapping("/lotes/{loteId}")
    @PreAuthorize("hasAuthority('LOTES_EDITAR')")
    public LoteResponse actualizarLote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long loteId,
            @Valid @RequestBody LoteActualizarRequest request
    ) {
        return inventarioService.actualizarLote(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                loteId,
                request
        );
    }

    @PatchMapping("/lotes/{loteId}/estado")
    @PreAuthorize("hasAuthority('LOTES_CAMBIAR_ESTADO')")
    public LoteResponse cambiarEstado(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long loteId,
            @Valid @RequestBody CambioEstadoLoteRequest request
    ) {
        return inventarioService.cambiarEstado(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                loteId,
                request
        );
    }

    @PatchMapping("/lotes/{loteId}/precio")
    @PreAuthorize("hasAuthority('PRECIOS_LOTE_CAMBIAR')")
    public LoteResponse cambiarPrecio(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long loteId,
            @Valid @RequestBody CambioPrecioLoteRequest request
    ) {
        return inventarioService.cambiarPrecio(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                loteId,
                request
        );
    }

    @GetMapping("/lotes/{loteId}/historial-estados")
    @PreAuthorize("hasAuthority('LOTES_VER')")
    public List<HistorialEstadoLoteResponse> historialEstados(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long loteId
    ) {
        return inventarioService.historialEstados(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                loteId
        );
    }

    @GetMapping("/lotes/{loteId}/historial-precios")
    @PreAuthorize("hasAuthority('LOTES_VER')")
    public List<HistorialPrecioLoteResponse> historialPrecios(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId,
            @PathVariable Long loteId
    ) {
        return inventarioService.historialPrecios(
                ContextoUsuario.empresa(jwt),
                proyectoId,
                loteId
        );
    }

    @GetMapping("/inventario/resumen")
    @PreAuthorize("""
            hasAuthority('LOTES_VER')
            or hasAuthority('LOTES_DISPONIBILIDAD_VER')
            """)
    public InventarioResumenResponse resumen(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long proyectoId
    ) {
        return inventarioService.resumen(
                ContextoUsuario.empresa(jwt),
                proyectoId
        );
    }
}
