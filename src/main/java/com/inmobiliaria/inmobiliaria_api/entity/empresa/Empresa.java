package com.inmobiliaria.inmobiliaria_api.entity.empresa;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "empresas")
public class Empresa extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String codigo;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(nullable = false, length = 30)
    private String nit;

    @Column(length = 240)
    private String direccion;

    @Column(length = 100)
    private String ciudad;

    @Column(length = 100)
    private String departamento;

    @Column(length = 40)
    private String telefono;

    @Column(length = 180)
    private String correo;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "zona_horaria", nullable = false, length = 60)
    private String zonaHoraria;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "limite_proyectos", nullable = false)
    private Integer limiteProyectos;

    @Column(name = "limite_usuarios", nullable = false)
    private Integer limiteUsuarios;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Empresa() {
    }

    public Empresa(String codigo, String nombre, String nit) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.nit = nit;
        this.zonaHoraria = "America/Bogota";
        this.moneda = "COP";
        this.estado = "ACTIVA";
        this.limiteProyectos = 1;
        this.limiteUsuarios = 5;
        this.version = 0L;
    }

    public void actualizar(
            String codigo,
            String nombre,
            String nit,
            String direccion,
            String ciudad,
            String departamento,
            String telefono,
            String correo,
            String logoUrl,
            String zonaHoraria,
            String moneda,
            Integer limiteProyectos,
            Integer limiteUsuarios
    ) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.nit = nit;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.departamento = departamento;
        this.telefono = telefono;
        this.correo = correo;
        this.logoUrl = logoUrl;
        this.zonaHoraria = zonaHoraria;
        this.moneda = moneda;
        this.limiteProyectos = limiteProyectos;
        this.limiteUsuarios = limiteUsuarios;
    }

    public void cambiarEstado(String nuevoEstado) {
        this.estado = nuevoEstado;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNit() {
        return nit;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getCiudad() {
        return ciudad;
    }

    public String getDepartamento() {
        return departamento;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public String getMoneda() {
        return moneda;
    }

    public String getEstado() {
        return estado;
    }

    public Integer getLimiteProyectos() {
        return limiteProyectos;
    }

    public Integer getLimiteUsuarios() {
        return limiteUsuarios;
    }

    public Long getVersion() {
        return version;
    }
}
