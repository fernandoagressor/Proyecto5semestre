package com.inmobiliaria.inmobiliaria_api.dto.catalogo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Datos del catálogo público: solo lo que puede ver cualquier visitante. */
public final class CatalogoDtos {
    private CatalogoDtos() { }

    public record Empresa(String codigo, String nombre, String logoUrl, String ciudad, String telefono,
                          String correo, List<ProyectoResumen> proyectos) { }

    public record ProyectoResumen(Long id, String codigo, String nombre, String ciudad, String departamento,
                                  String descripcion, String imagenUrl, int porcentajeAvance, long lotesDisponibles,
                                  BigDecimal precioDesde, BigDecimal areaMinima, BigDecimal areaMaxima) { }

    public record ProyectoDetalle(ProyectoResumen proyecto, String direccion, List<Imagen> imagenes,
                                  List<Lote> lotes) { }

    public record Imagen(Long id, String url, String titulo) { }

    public record Lote(Long id, String codigo, String numero, String etapa, String manzana, BigDecimal areaM2,
                       BigDecimal frenteM, BigDecimal fondoM, BigDecimal valor, String ubicacion) { }

    /** Formulario de contacto. {@code sitioWeb} es una trampa para robots: debe llegar vacío. */
    public record Contacto(
            Long proyectoId,
            @NotBlank @Size(max = 160) String nombres,
            @Size(max = 40) @Pattern(regexp = "^[0-9+()\\s-]*$") String telefono,
            @Email @Size(max = 180) String correo,
            @Size(max = 1000) String mensaje,
            @AssertTrue(message = "Debe autorizar el tratamiento de sus datos") boolean aceptaTratamiento,
            @Size(max = 0) String sitioWeb
    ) { }

    public record EstadoCatalogo(String codigo, boolean publico) { }

    public record CambioCatalogo(boolean publico) { }

    public record ImagenRequest(@NotBlank @Size(max = 500) @Pattern(regexp = "^https://\\S+$",
            message = "Use un enlace https") String url, @Size(max = 160) String titulo) { }
}
