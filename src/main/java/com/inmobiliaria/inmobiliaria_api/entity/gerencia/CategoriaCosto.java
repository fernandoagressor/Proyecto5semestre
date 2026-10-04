package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias_costo")
public class CategoriaCosto extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(nullable = false, length = 40)
    private String codigo;
    @Column(nullable = false, length = 140)
    private String nombre;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCategoriaCosto tipo;
    @Column(nullable = false)
    private boolean activo;

    protected CategoriaCosto() { }

    public CategoriaCosto(Long empresaId, String codigo, String nombre, TipoCategoriaCosto tipo) {
        if (codigo == null || codigo.isBlank() || nombre == null || nombre.isBlank() || tipo == null) {
            throw new ReglaNegocioException("Código, nombre y tipo de categoría son obligatorios");
        }
        this.empresaId = empresaId;
        this.codigo = codigo.trim().toUpperCase();
        this.nombre = nombre.trim();
        this.tipo = tipo;
        this.activo = true;
    }

    public void inactivar() { activo = false; }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public TipoCategoriaCosto getTipo() { return tipo; }
    public boolean isActivo() { return activo; }
}
