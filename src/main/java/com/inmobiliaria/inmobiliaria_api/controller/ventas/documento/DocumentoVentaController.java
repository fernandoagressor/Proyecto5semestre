package com.inmobiliaria.inmobiliaria_api.controller.ventas.documento;

import com.inmobiliaria.inmobiliaria_api.dto.ventas.documento.DocumentoVentaResponse;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.ventas.documento.DocumentoVentaService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ventas/documentos")
public class DocumentoVentaController {

    private final DocumentoVentaService documentoVentaService;

    public DocumentoVentaController(DocumentoVentaService documentoVentaService) {
        this.documentoVentaService = documentoVentaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENTOS_GENERAR')")
    public DocumentoVentaResponse subir(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long proyectoId,
            @RequestParam(defaultValue = "CONFIDENCIAL") String clasificacion,
            @RequestPart("archivo") MultipartFile archivo
    ) {
        return documentoVentaService.guardar(
                ContextoUsuario.empresa(jwt), proyectoId, clasificacion, archivo);
    }

    @GetMapping("/{archivoId}")
    @PreAuthorize("hasAuthority('DOCUMENTOS_DESCARGAR')")
    public ResponseEntity<?> descargar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long archivoId
    ) {
        DocumentoVentaService.Descarga descarga = documentoVentaService
                .descargar(ContextoUsuario.empresa(jwt), archivoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.mimeType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(descarga.nombre(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(descarga.recurso());
    }
}
