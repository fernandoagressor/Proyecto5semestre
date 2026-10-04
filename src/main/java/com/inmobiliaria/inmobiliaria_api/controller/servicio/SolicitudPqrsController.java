package com.inmobiliaria.inmobiliaria_api.controller.servicio;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.servicio.SolicitudDtos;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.EstadoSolicitud;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.TipoSolicitud;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.servicio.SolicitudPqrsService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/solicitudes")
public class SolicitudPqrsController {
    private final SolicitudPqrsService service;

    public SolicitudPqrsController(SolicitudPqrsService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('SOLICITUDES_VER')")
    public PaginaResponse<SolicitudDtos.Response> listar(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(required = false) EstadoSolicitud estado,
                                                         @RequestParam(required = false) TipoSolicitud tipo,
                                                         @RequestParam(required = false) Long clienteId,
                                                         @RequestParam(required = false) Long asignadoA,
                                                         @RequestParam(defaultValue = "0") int pagina,
                                                         @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(ContextoUsuario.empresa(jwt), estado, tipo, clienteId, asignadoA, pagina, tamano);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SOLICITUDES_CREAR')")
    public SolicitudDtos.Response crear(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody SolicitudDtos.CrearRequest request) {
        return service.crear(ContextoUsuario.empresa(jwt), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SOLICITUDES_VER')")
    public SolicitudDtos.Response obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.obtener(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/{id}/mensajes")
    @PreAuthorize("hasAuthority('SOLICITUDES_VER')")
    public List<SolicitudDtos.MensajeResponse> mensajes(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.mensajes(ContextoUsuario.empresa(jwt), id);
    }

    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SOLICITUDES_RESPONDER')")
    public SolicitudDtos.MensajeResponse responder(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                   @Valid @RequestBody SolicitudDtos.MensajeRequest request) {
        return service.responder(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/asignacion")
    @PreAuthorize("hasAuthority('SOLICITUDES_ASIGNAR')")
    public SolicitudDtos.Response asignar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                          @Valid @RequestBody SolicitudDtos.AsignarRequest request) {
        return service.asignar(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('SOLICITUDES_CAMBIAR_ESTADO')")
    public SolicitudDtos.Response cambiarEstado(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                @Valid @RequestBody SolicitudDtos.EstadoRequest request) {
        return service.cambiarEstado(ContextoUsuario.empresa(jwt), id, request);
    }

    @PatchMapping("/{id}/cerrar")
    @PreAuthorize("hasAuthority('SOLICITUDES_CERRAR')")
    public SolicitudDtos.Response cerrar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                         @RequestBody(required = false) SolicitudDtos.CerrarRequest request) {
        return service.cerrar(ContextoUsuario.empresa(jwt), id, request == null ? null : request.motivo());
    }

    @PostMapping("/{id}/adjuntos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SOLICITUDES_RESPONDER')")
    public SolicitudDtos.AdjuntoResponse adjuntar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                  @Valid @RequestBody SolicitudDtos.AdjuntoRequest request) {
        return service.adjuntar(ContextoUsuario.empresa(jwt), id, request);
    }

    @PostMapping(value = "/{id}/adjuntos/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SOLICITUDES_RESPONDER')")
    public SolicitudDtos.AdjuntoResponse subirAdjunto(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam(required = false) Long mensajeId,
            @RequestPart("archivo") MultipartFile archivo) {
        return service.subirAdjunto(ContextoUsuario.empresa(jwt), id, mensajeId, archivo);
    }

    @GetMapping("/{id}/adjuntos")
    @PreAuthorize("hasAuthority('SOLICITUDES_VER')")
    public List<SolicitudDtos.AdjuntoResponse> adjuntos(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.adjuntos(ContextoUsuario.empresa(jwt), id);
    }

    @GetMapping("/{id}/adjuntos/{archivoId}/descargar")
    @PreAuthorize("hasAuthority('SOLICITUDES_VER')")
    public ResponseEntity<?> descargarAdjunto(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long id,
                                               @PathVariable Long archivoId) {
        var descarga = service.descargarAdjunto(ContextoUsuario.empresa(jwt), id, archivoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(descarga.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(descarga.recurso());
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('SOLICITUDES_ASIGNAR')")
    public List<SolicitudDtos.HistorialResponse> historial(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.historial(ContextoUsuario.empresa(jwt), id);
    }

    @PostMapping("/{id}/encuesta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SOLICITUDES_VER')")
    public void encuesta(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                         @Valid @RequestBody SolicitudDtos.EncuestaRequest request) {
        service.responderEncuesta(ContextoUsuario.empresa(jwt), id, request);
    }
}
