package com.inmobiliaria.inmobiliaria_api.entity.crm;

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
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity
@Table(name = "prospectos")
public class Prospecto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "persona_id")
    private Long personaId;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "asesor_id")
    private Long asesorId;

    @Column(nullable = false, length = 160)
    private String nombres;

    @Column(length = 40)
    private String telefono;

    @Column(length = 180)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private FuenteProspecto fuente;

    @Column(precision = 19, scale = 2)
    private BigDecimal presupuesto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TemperaturaProspecto temperatura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProspecto estado;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Prospecto() {
    }

    public Prospecto(
            Long empresaId,
            Long proyectoId,
            Long asesorId,
            String nombres,
            String telefono,
            String correo,
            FuenteProspecto fuente,
            BigDecimal presupuesto,
            TemperaturaProspecto temperatura,
            String observaciones
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.asesorId = asesorId;
        this.estado = EstadoProspecto.NUEVO;
        actualizarDatos(
                proyectoId,
                nombres,
                telefono,
                correo,
                fuente,
                presupuesto,
                temperatura,
                observaciones
        );
    }

    public void actualizarDatos(
            Long proyectoId,
            String nombres,
            String telefono,
            String correo,
            FuenteProspecto fuente,
            BigDecimal presupuesto,
            TemperaturaProspecto temperatura,
            String observaciones
    ) {
        if (estado == EstadoProspecto.CONVERTIDO) {
            throw new ReglaNegocioException(
                    "Un prospecto convertido no puede editarse"
            );
        }
        if (presupuesto != null && presupuesto.signum() < 0) {
            throw new ReglaNegocioException(
                    "El presupuesto del prospecto no puede ser negativo"
            );
        }
        this.proyectoId = proyectoId;
        this.nombres = nombres;
        this.telefono = telefono;
        this.correo = correo;
        this.fuente = fuente;
        this.presupuesto = presupuesto;
        this.temperatura = temperatura;
        this.observaciones = observaciones;
    }

    public void cambiarCalificacion(
            EstadoProspecto nuevoEstado,
            TemperaturaProspecto nuevaTemperatura,
            String justificacion
    ) {
        if (nuevoEstado == EstadoProspecto.CONVERTIDO) {
            throw new ReglaNegocioException(
                    "La conversión se realiza desde el proceso de venta"
            );
        }
        if (estado == EstadoProspecto.CONVERTIDO) {
            throw new ReglaNegocioException(
                    "Un prospecto convertido no admite cambios"
            );
        }
        if (estado == EstadoProspecto.DESCARTADO
                && nuevoEstado != EstadoProspecto.DESCARTADO) {
            throw new ReglaNegocioException(
                    "Un prospecto descartado no puede reactivarse sin un flujo autorizado"
            );
        }
        if (nuevoEstado == EstadoProspecto.DESCARTADO
                && (justificacion == null || justificacion.isBlank())) {
            throw new ReglaNegocioException(
                    "Debe indicar el motivo para descartar el prospecto"
            );
        }
        estado = nuevoEstado;
        temperatura = nuevaTemperatura;
        if (justificacion != null && !justificacion.isBlank()) {
            observaciones = justificacion;
        }
    }

    public void reasignar(Long nuevoAsesorId) {
        if (estado == EstadoProspecto.CONVERTIDO) {
            throw new ReglaNegocioException(
                    "Un prospecto convertido no puede reasignarse"
            );
        }
        asesorId = nuevoAsesorId;
    }

    public void convertirPorVenta(Long nuevoClienteId) {
        if (estado == EstadoProspecto.CONVERTIDO) {
            if (clienteId != null && clienteId.equals(nuevoClienteId)) {
                return;
            }
            throw new ReglaNegocioException(
                    "El prospecto ya fue convertido para otro cliente"
            );
        }
        if (estado != EstadoProspecto.CALIFICADO) {
            throw new ReglaNegocioException(
                    "Solo un prospecto calificado puede convertirse en venta"
            );
        }
        clienteId = nuevoClienteId;
        estado = EstadoProspecto.CONVERTIDO;
        temperatura = TemperaturaProspecto.CALIENTE;
    }

    public void reabrirPorAnulacionContrato() {
        if (estado != EstadoProspecto.CONVERTIDO) {
            return;
        }
        estado = EstadoProspecto.CALIFICADO;
        temperatura = TemperaturaProspecto.CALIENTE;
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

    public Long getPersonaId() {
        return personaId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public Long getAsesorId() {
        return asesorId;
    }

    public String getNombres() {
        return nombres;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public FuenteProspecto getFuente() {
        return fuente;
    }

    public BigDecimal getPresupuesto() {
        return presupuesto;
    }

    public TemperaturaProspecto getTemperatura() {
        return temperatura;
    }

    public EstadoProspecto getEstado() {
        return estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public Long getVersion() {
        return version;
    }
}
