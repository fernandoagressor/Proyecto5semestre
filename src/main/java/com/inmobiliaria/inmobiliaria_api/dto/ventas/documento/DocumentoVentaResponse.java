package com.inmobiliaria.inmobiliaria_api.dto.ventas.documento;

import java.time.LocalDateTime;

public record DocumentoVentaResponse(
        Long id,
        Long empresaId,
        Long proyectoId,
        String nombreOriginal,
        String mimeType,
        long tamanoBytes,
        String sha256,
        String clasificacion,
        LocalDateTime creadoEn
) {
}
