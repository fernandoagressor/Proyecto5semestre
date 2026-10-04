package com.inmobiliaria.inmobiliaria_api.entity.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias_conocimiento")
public class CategoriaConocimiento extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id")
    private Long empresaId;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(length = 500)
    private String descripcion;
    @Column(nullable = false)
    private short orden;
    @Column(nullable = false)
    private boolean activo;

    protected CategoriaConocimiento() { }

    public CategoriaConocimiento(Long empresaId, String nombre, String descripcion, short orden) {
        this.empresaId = empresaId;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.orden = orden;
        this.activo = true;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public short getOrden() { return orden; }
    public boolean isActivo() { return activo; }
}
