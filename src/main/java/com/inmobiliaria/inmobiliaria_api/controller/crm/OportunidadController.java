package com.inmobiliaria.inmobiliaria_api.controller.crm;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ActividadComercialRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.ActividadComercialResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CambioEstadoCitaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CambioEtapaOportunidadRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CierrePerdidaRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CitaComercialRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.CitaComercialResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.HistorialEtapaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadActualizarRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadLoteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadLoteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadRequest;
import com.inmobiliaria.inmobiliaria_api.dto.crm.OportunidadResponse;
import com.inmobiliaria.inmobiliaria_api.dto.crm.SeleccionLoteRequest;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EtapaOportunidad;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.crm.OportunidadService;
import com.inmobiliaria.inmobiliaria_api.service.crm.SeguimientoComercialService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/oportunidades")
public class OportunidadController {

    private final OportunidadService oportunidadService;
    private final SeguimientoComercialService seguimientoService;

    public OportunidadController(
            OportunidadService oportunidadService,
            SeguimientoComercialService seguimientoService
    ) {
        this.oportunidadService = oportunidadService;
        this.seguimientoService = seguimientoService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OPORTUNIDADES_VER')")
    public PaginaResponse<OportunidadResponse> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoOportunidad estado,
            @RequestParam(required = false) EtapaOportunidad etapa,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(required = false) Long asesorId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano
    ) {
        return oportunidadService.listar(
                ContextoUsuario.empresa(jwt),
                texto,
                estado,
                etapa,
                proyectoId,
                asesorId,
                pagina,
                tamano
        );
    }

    @GetMapping("/{oportunidadId}")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_VER')")
    public OportunidadResponse obtener(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId
    ) {
        return oportunidadService.obtener(
                ContextoUsuario.empresa(jwt),
                oportunidadId
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OPORTUNIDADES_CREAR')")
    public OportunidadResponse crear(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OportunidadRequest request
    ) {
        return oportunidadService.crear(
                ContextoUsuario.empresa(jwt),
                request
        );
    }

    @PutMapping("/{oportunidadId}")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_EDITAR')")
    public OportunidadResponse actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @Valid @RequestBody OportunidadActualizarRequest request
    ) {
        return oportunidadService.actualizar(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                request
        );
    }

    @PatchMapping("/{oportunidadId}/etapa")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_AVANZAR')")
    public OportunidadResponse cambiarEtapa(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @Valid @RequestBody CambioEtapaOportunidadRequest request
    ) {
        return oportunidadService.cambiarEtapa(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                request
        );
    }

    @PatchMapping("/{oportunidadId}/cerrar-perdida")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_CERRAR_PERDIDA')")
    public OportunidadResponse cerrarPerdida(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @Valid @RequestBody CierrePerdidaRequest request
    ) {
        return oportunidadService.cerrarPerdida(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                request.motivo()
        );
    }

    @PatchMapping("/{oportunidadId}/reabrir")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_REABRIR')")
    public OportunidadResponse reabrir(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId
    ) {
        return oportunidadService.reabrir(
                ContextoUsuario.empresa(jwt),
                oportunidadId
        );
    }

    @GetMapping("/{oportunidadId}/lotes")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_VER')")
    public List<OportunidadLoteResponse> listarLotes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId
    ) {
        return oportunidadService.listarLotes(
                ContextoUsuario.empresa(jwt),
                oportunidadId
        );
    }

    @PostMapping("/{oportunidadId}/lotes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OPORTUNIDADES_EDITAR')")
    public OportunidadLoteResponse agregarLote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @Valid @RequestBody OportunidadLoteRequest request
    ) {
        return oportunidadService.agregarLote(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                request.loteId(),
                request.prioridad()
        );
    }

    @DeleteMapping("/{oportunidadId}/lotes/{loteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('OPORTUNIDADES_EDITAR')")
    public void quitarLote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @PathVariable Long loteId
    ) {
        oportunidadService.quitarLote(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                loteId
        );
    }

    @PatchMapping("/{oportunidadId}/lotes/{loteId}/seleccion")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_EDITAR')")
    public OportunidadLoteResponse seleccionarLote(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @PathVariable Long loteId,
            @Valid @RequestBody SeleccionLoteRequest request
    ) {
        return oportunidadService.seleccionarLote(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                loteId,
                request.seleccionado()
        );
    }

    @GetMapping("/{oportunidadId}/historial-etapas")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_VER')")
    public List<HistorialEtapaResponse> historialEtapas(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId
    ) {
        return oportunidadService.historialEtapas(
                ContextoUsuario.empresa(jwt),
                oportunidadId
        );
    }

    @GetMapping("/{oportunidadId}/actividades")
    @PreAuthorize("hasAuthority('OPORTUNIDADES_VER')")
    public List<ActividadComercialResponse> listarActividades(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId
    ) {
        return seguimientoService.listarActividades(
                ContextoUsuario.empresa(jwt),
                oportunidadId
        );
    }

    @PostMapping("/{oportunidadId}/actividades")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AGENDA_GESTIONAR')")
    public ActividadComercialResponse registrarActividad(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @Valid @RequestBody ActividadComercialRequest request
    ) {
        return seguimientoService.registrarActividad(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                request
        );
    }

    @GetMapping("/{oportunidadId}/citas")
    @PreAuthorize("hasAuthority('AGENDA_VER')")
    public List<CitaComercialResponse> listarCitas(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId
    ) {
        return seguimientoService.listarCitas(
                ContextoUsuario.empresa(jwt),
                oportunidadId
        );
    }

    @PostMapping("/{oportunidadId}/citas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AGENDA_GESTIONAR')")
    public CitaComercialResponse crearCita(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @Valid @RequestBody CitaComercialRequest request
    ) {
        return seguimientoService.crearCita(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                request
        );
    }

    @PutMapping("/{oportunidadId}/citas/{citaId}/programacion")
    @PreAuthorize("hasAuthority('AGENDA_GESTIONAR')")
    public CitaComercialResponse reprogramarCita(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @PathVariable Long citaId,
            @Valid @RequestBody CitaComercialRequest request
    ) {
        return seguimientoService.reprogramarCita(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                citaId,
                request
        );
    }

    @PatchMapping("/{oportunidadId}/citas/{citaId}/estado")
    @PreAuthorize("hasAuthority('AGENDA_GESTIONAR')")
    public CitaComercialResponse cambiarEstadoCita(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long oportunidadId,
            @PathVariable Long citaId,
            @Valid @RequestBody CambioEstadoCitaRequest request
    ) {
        return seguimientoService.cambiarEstadoCita(
                ContextoUsuario.empresa(jwt),
                oportunidadId,
                citaId,
                request
        );
    }
}
