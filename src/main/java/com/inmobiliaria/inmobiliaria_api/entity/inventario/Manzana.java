package com.inmobiliaria.inmobiliaria_api.entity.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "manzanas")
public class Manzana extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "etapa_id", nullable = false)
    private Long etapaId;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Integer secuencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEtapa estado;

    protected Manzana() {
    }

    public Manzana(
            Long empresaId,
            Long proyectoId,
            Long etapaId,
            String codigo,
            String nombre,
            Integer secuencia
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.etapaId = etapaId;
        actualizar(codigo, nombre, secuencia, EstadoEtapa.ACTIVA);
    }

    public void actualizar(
            String codigo,
            String nombre,
            Integer secuencia,
            EstadoEtapa estado
    ) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.secuencia = secuencia;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public Long getEtapaId() {
        return etapaId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public Integer getSecuencia() {
        return secuencia;
    }

    public EstadoEtapa getEstado() {
        return estado;
    }
}
