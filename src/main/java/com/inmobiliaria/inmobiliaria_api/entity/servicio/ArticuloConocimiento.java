package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/** Artículo de la base de conocimiento, guardado en MongoDB (rama 10). Sin empresa es global. */
@Document(collection = ArticuloConocimiento.COLECCION)
public class ArticuloConocimiento {
    public static final String COLECCION = "articulos_conocimiento";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long categoriaId;
    private String titulo;
    private String slug;
    private String contenido;
    private boolean visibleClientes;
    private String estado;
    private Long autorId;
    private LocalDateTime publicadoEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    @Version
    private Long version;

    protected ArticuloConocimiento() { }

    public ArticuloConocimiento(Long empresaId, Long categoriaId, String titulo, String slug,
                                String contenido, boolean visibleClientes, Long autorId) {
        this.empresaId = empresaId;
        this.categoriaId = categoriaId;
        this.titulo = titulo;
        this.slug = slug;
        this.contenido = contenido;
        this.visibleClientes = visibleClientes;
        this.autorId = autorId;
        this.estado = "BORRADOR";
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    public void actualizar(Long categoriaId, String titulo, String slug, String contenido,
                           boolean visibleClientes) {
        if ("ARCHIVADO".equals(estado)) {
            throw new ReglaNegocioException("Un artículo archivado no puede editarse");
        }
        this.categoriaId = categoriaId;
        this.titulo = titulo;
        this.slug = slug;
        this.contenido = contenido;
        this.visibleClientes = visibleClientes;
    }

    public void publicar(LocalDateTime fecha) {
        if ("ARCHIVADO".equals(estado)) {
            throw new ReglaNegocioException("Un artículo archivado no puede publicarse");
        }
        estado = "PUBLICADO";
        publicadoEn = fecha;
    }

    public void archivar() { estado = "ARCHIVADO"; }

    /** Asigna el identificador de un registro nuevo. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("El artículo ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getCategoriaId() { return categoriaId; }
    public String getTitulo() { return titulo; }
    public String getSlug() { return slug; }
    public String getContenido() { return contenido; }
    public boolean isVisibleClientes() { return visibleClientes; }
    public String getEstado() { return estado; }
    public Long getAutorId() { return autorId; }
    public LocalDateTime getPublicadoEn() { return publicadoEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
}
