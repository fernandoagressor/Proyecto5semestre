package com.inmobiliaria.inmobiliaria_api.controller.documentos;

import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.documentos.DocumentosContratoService;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaExportacionService;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.ReporteExportacionService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Descargas generadas al vuelo: reportes (CSV, XLSX o PDF), bitácora de auditoría y documentos
 * de un contrato (estado de cuenta, paz y salvo, cobro de mora). Nunca se guardan en caché.
 */
@RestController
public class DescargasController {

    private final ReporteExportacionService reportes;
    private final AuditoriaExportacionService auditoria;
    private final DocumentosContratoService documentos;

    public DescargasController(ReporteExportacionService reportes, AuditoriaExportacionService auditoria,
                               DocumentosContratoService documentos) {
        this.reportes = reportes;
        this.auditoria = auditoria;
        this.documentos = documentos;
    }

    @GetMapping("/api/v1/exportaciones/reportes/{tipo}")
    @PreAuthorize("hasAuthority('REPORTES_EXPORTAR')")
    public ResponseEntity<byte[]> reporte(@AuthenticationPrincipal Jwt jwt, @PathVariable String tipo,
            @RequestParam(defaultValue = "PDF") String formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long proyectoId) {
        var archivo = reportes.exportar(ContextoUsuario.empresa(jwt), tipo, formato, desde, hasta, proyectoId);
        return descarga(archivo.nombre(), archivo.mime(), archivo.contenido());
    }

    @GetMapping("/api/v1/exportaciones/auditoria")
    @PreAuthorize("hasAuthority('AUDITORIA_EXPORTAR')")
    public ResponseEntity<byte[]> auditoria(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "XLSX") String formato,
            @RequestParam(required = false) Long proyectoId, @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String modulo, @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        var archivo = auditoria.exportar(ContextoUsuario.empresa(jwt), formato, proyectoId, actorId, modulo, accion,
                desde, hasta);
        return descarga(archivo.nombre(), archivo.mime(), archivo.contenido());
    }

    @GetMapping("/api/v1/contratos/{contratoId}/documentos/estado-cuenta")
    @PreAuthorize("hasAuthority('ESTADOS_CUENTA_GENERAR')")
    public ResponseEntity<byte[]> estadoCuenta(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contratoId) {
        var pdf = documentos.estadoCuenta(ContextoUsuario.empresa(jwt), contratoId);
        return descarga(pdf.nombre(), MediaType.APPLICATION_PDF_VALUE, pdf.contenido());
    }

    @GetMapping("/api/v1/contratos/{contratoId}/documentos/paz-y-salvo")
    @PreAuthorize("hasAuthority('PAZ_SALVO_GENERAR')")
    public ResponseEntity<byte[]> pazYSalvo(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contratoId) {
        var pdf = documentos.pazYSalvo(ContextoUsuario.empresa(jwt), contratoId);
        return descarga(pdf.nombre(), MediaType.APPLICATION_PDF_VALUE, pdf.contenido());
    }

    @GetMapping("/api/v1/contratos/{contratoId}/documentos/cobro-mora")
    @PreAuthorize("hasAuthority('MORA_GESTIONAR')")
    public ResponseEntity<byte[]> cobroMora(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contratoId) {
        var pdf = documentos.cobroMora(ContextoUsuario.empresa(jwt), contratoId);
        return descarga(pdf.nombre(), MediaType.APPLICATION_PDF_VALUE, pdf.contenido());
    }

    /** Estado de cuenta del propio cliente, desde su portal. */
    @GetMapping("/api/v1/portal/contratos/{contratoId}/estado-cuenta")
    @PreAuthorize("hasAuthority('CUOTAS_VER')")
    public ResponseEntity<byte[]> estadoCuentaPortal(@AuthenticationPrincipal Jwt jwt, @PathVariable Long contratoId) {
        var pdf = documentos.estadoCuentaCliente(ContextoUsuario.empresa(jwt), contratoId);
        return descarga(pdf.nombre(), MediaType.APPLICATION_PDF_VALUE, pdf.contenido());
    }

    private static ResponseEntity<byte[]> descarga(String nombre, String mime, byte[] contenido) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
                .body(contenido);
    }
}
