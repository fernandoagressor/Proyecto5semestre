package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "articulos_conocimiento")
public class ArticuloConocimiento extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id")
    private Long empresaId;
    @Column(name = "categoria_id", nullable = false)
    private Long categoriaId;
    @Column(nullable = false, length = 220)
    private String titulo;
    @Column(nullable = false, length = 240)
    private String slug;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String contenido;
    @Column(name = "visible_clientes", nullable = false)
    private boolean visibleClientes;
    @Column(nullable = false, length = 20)
    private String estado;
    @Column(name = "autor_id")
    private Long autorId;
    @Column(name = "publicado_en")
    private LocalDateTime publicadoEn;
    @Version
    @Column(nullable = false)
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
}
