package com.inmobiliaria.inmobiliaria_api.dto.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.servicio.ArticuloConocimiento;
import com.inmobiliaria.inmobiliaria_api.entity.servicio.CategoriaConocimiento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class ConocimientoDtos {
    private ConocimientoDtos() { }

    public record CategoriaRequest(@NotBlank @Size(max = 120) String nombre,
                                   @Size(max = 500) String descripcion,
                                   @Min(1) @Max(32000) short orden) { }

    public record ArticuloRequest(@NotNull Long categoriaId,
                                  @NotBlank @Size(max = 220) String titulo,
                                  @Size(max = 240) String slug,
                                  @NotBlank @Size(max = 1000000) String contenido,
                                  boolean visibleClientes) { }

    public record CategoriaResponse(Long id, String nombre, String descripcion, short orden) {
        public static CategoriaResponse from(CategoriaConocimiento c) {
            return new CategoriaResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.getOrden());
        }
    }

    public record ArticuloResponse(Long id, Long categoriaId, String titulo, String slug,
                                   String contenido, boolean visibleClientes, String estado,
                                   Long autorId, LocalDateTime publicadoEn, LocalDateTime actualizadoEn) {
        public static ArticuloResponse from(ArticuloConocimiento a) {
            return new ArticuloResponse(a.getId(), a.getCategoriaId(), a.getTitulo(), a.getSlug(),
                    a.getContenido(), a.isVisibleClientes(), a.getEstado(), a.getAutorId(),
                    a.getPublicadoEn(), a.getActualizadoEn());
        }
    }
}
