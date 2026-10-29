package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Categoría de la base de conocimiento, guardada en MongoDB (rama 10). Sin empresa es global. */
@Document(collection = CategoriaConocimiento.COLECCION)
public class CategoriaConocimiento {
    public static final String COLECCION = "categorias_conocimiento";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private String nombre;
    private String descripcion;
    private short orden;
    private boolean activo;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    protected CategoriaConocimiento() { }

    public CategoriaConocimiento(Long empresaId, String nombre, String descripcion, short orden) {
        this.empresaId = empresaId;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.orden = orden;
        this.activo = true;
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    /** Asigna el identificador de un registro nuevo. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La categoría ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public short getOrden() { return orden; }
    public boolean isActivo() { return activo; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
