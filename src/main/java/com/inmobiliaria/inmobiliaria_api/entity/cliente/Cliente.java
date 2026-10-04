package com.inmobiliaria.inmobiliaria_api.entity.cliente;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "clientes")
public class Cliente extends EntidadAuditable {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "codigo_cliente", nullable = false, length = 40)
    private String codigoCliente;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(length = 80)
    private String origen;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDate fechaAlta;

    protected Cliente() {
    }

    public Cliente(
            Long empresaId,
            Long personaId,
            String codigoCliente,
            String origen,
            String observaciones
    ) {
        this.empresaId = empresaId;
        this.personaId = personaId;
        this.codigoCliente = codigoCliente;
        this.origen = opcional(origen);
        this.observaciones = opcional(observaciones);
        this.estado = "ACTIVO";
        this.fechaAlta = LocalDate.now(BOGOTA);
    }

    public Long getId() {
        return id;
    }

    public Long getEmpresaId() {
        return empresaId;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getCodigoCliente() {
        return codigoCliente;
    }

    public String getEstado() {
        return estado;
    }

    public String getOrigen() {
        return origen;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public LocalDate getFechaAlta() {
        return fechaAlta;
    }

    public void actualizar(String nuevoOrigen, String nuevasObservaciones) {
        origen = opcional(nuevoOrigen);
        observaciones = opcional(nuevasObservaciones);
    }

    public void cambiarEstado(String nuevoEstado) {
        String normalizado = nuevoEstado == null
                ? ""
                : nuevoEstado.trim().toUpperCase();
        if (!normalizado.equals("ACTIVO")
                && !normalizado.equals("INACTIVO")
                && !normalizado.equals("PENDIENTE")) {
            throw new IllegalArgumentException("El estado del cliente no es válido");
        }
        estado = normalizado;
    }

    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
