package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Prospecto comercial, guardado en MongoDB (rama 8). Los montos se guardan como Decimal128.
 * La conversión por venta la aplica {@code ProcesadorPendientesCrm} cuando se firma el contrato.
 */
@Document(collection = Prospecto.COLECCION)
public class Prospecto {

    public static final String COLECCION = "prospectos";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private Long personaId;
    private Long clienteId;
    private Long asesorId;
    private String nombres;
    private String telefono;
    private String correo;
    private FuenteProspecto fuente;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal presupuesto;
    private TemperaturaProspecto temperatura;
    private EstadoProspecto estado;
    private String observaciones;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    @Version
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
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
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

    /** Asigna el identificador de un prospecto nuevo. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("El prospecto ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    /**
     * Aplica la venta firmada (desde la cola de pendientes). La venta ya está confirmada en MySQL,
     * así que solo se rechaza si el prospecto ya se convirtió para otro cliente.
     *
     * @return si hubo cambios
     */
    public boolean aplicarVentaGanada(Long nuevoClienteId) {
        if (estado == EstadoProspecto.CONVERTIDO) {
            if (clienteId != null && !clienteId.equals(nuevoClienteId)) {
                throw new ReglaNegocioException("El prospecto ya fue convertido para otro cliente");
            }
            return false;
        }
        clienteId = nuevoClienteId;
        estado = EstadoProspecto.CONVERTIDO;
        temperatura = TemperaturaProspecto.CALIENTE;
        return true;
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

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
