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
import java.time.LocalDateTime;

@Entity
@Table(name = "oportunidades")
public class Oportunidad extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(name = "prospecto_id", nullable = false)
    private Long prospectoId;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "asesor_id", nullable = false)
    private Long asesorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_actual", nullable = false, length = 40)
    private EtapaOportunidad etapaActual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOportunidad estado;

    @Column(name = "probabilidad_cierre", nullable = false, precision = 5, scale = 2)
    private BigDecimal probabilidadCierre;

    @Column(name = "valor_estimado", precision = 19, scale = 2)
    private BigDecimal valorEstimado;

    @Column(name = "fecha_ultima_actividad")
    private LocalDateTime fechaUltimaActividad;

    @Column(name = "fecha_proxima_accion")
    private LocalDateTime fechaProximaAccion;

    @Column(name = "motivo_perdida", length = 300)
    private String motivoPerdida;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Oportunidad() {
    }

    public Oportunidad(
            Long empresaId,
            Long proyectoId,
            String codigo,
            Long prospectoId,
            Long asesorId,
            BigDecimal valorEstimado,
            LocalDateTime fechaProximaAccion
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.prospectoId = prospectoId;
        this.asesorId = asesorId;
        this.etapaActual = EtapaOportunidad.PROSPECTO_RECIBIDO;
        this.estado = EstadoOportunidad.ABIERTA;
        this.probabilidadCierre = BigDecimal.valueOf(
                etapaActual.getProbabilidadSugerida()
        );
        actualizarDatos(valorEstimado, fechaProximaAccion);
    }

    public void actualizarDatos(
            BigDecimal valorEstimado,
            LocalDateTime fechaProximaAccion
    ) {
        exigirAbierta();
        if (valorEstimado != null && valorEstimado.signum() < 0) {
            throw new ReglaNegocioException(
                    "El valor estimado no puede ser negativo"
            );
        }
        this.valorEstimado = valorEstimado;
        this.fechaProximaAccion = fechaProximaAccion;
    }

    public EtapaOportunidad cambiarEtapa(
            EtapaOportunidad nuevaEtapa,
            BigDecimal nuevaProbabilidad
    ) {
        exigirAbierta();
        if (nuevaEtapa == etapaActual) {
            throw new ReglaNegocioException(
                    "La nueva etapa debe ser diferente a la actual"
            );
        }
        BigDecimal probabilidad = nuevaProbabilidad == null
                ? BigDecimal.valueOf(nuevaEtapa.getProbabilidadSugerida())
                : nuevaProbabilidad;
        if (probabilidad.signum() < 0
                || probabilidad.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ReglaNegocioException(
                    "La probabilidad debe estar entre 0 y 100"
            );
        }
        EtapaOportunidad anterior = etapaActual;
        etapaActual = nuevaEtapa;
        probabilidadCierre = probabilidad;
        return anterior;
    }

    public void registrarActividad(
            LocalDateTime fechaActividad,
            LocalDateTime proximaAccion
    ) {
        exigirAbierta();
        fechaUltimaActividad = fechaActividad;
        fechaProximaAccion = proximaAccion;
    }

    public void cerrarPerdida(String motivo, LocalDateTime fecha) {
        exigirAbierta();
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException(
                    "Debe indicar el motivo de pérdida"
            );
        }
        estado = EstadoOportunidad.PERDIDA;
        motivoPerdida = motivo;
        fechaCierre = fecha;
        probabilidadCierre = BigDecimal.ZERO;
        fechaProximaAccion = null;
    }

    public void reabrir() {
        if (estado != EstadoOportunidad.PERDIDA
                && estado != EstadoOportunidad.CANCELADA) {
            throw new ReglaNegocioException(
                    "Solo se pueden reabrir oportunidades perdidas o canceladas"
            );
        }
        estado = EstadoOportunidad.ABIERTA;
        motivoPerdida = null;
        fechaCierre = null;
        probabilidadCierre = BigDecimal.valueOf(
                etapaActual.getProbabilidadSugerida()
        );
    }

    public void vincularCliente(Long nuevoClienteId) {
        exigirAbierta();
        if (nuevoClienteId == null) {
            throw new ReglaNegocioException("El cliente es obligatorio");
        }
        if (clienteId != null && !clienteId.equals(nuevoClienteId)) {
            throw new ReglaNegocioException(
                    "La oportunidad ya está vinculada a otro cliente"
            );
        }
        clienteId = nuevoClienteId;
    }

    public void ganarPorContrato(LocalDateTime fecha) {
        exigirAbierta();
        etapaActual = EtapaOportunidad.CIERRE;
        estado = EstadoOportunidad.GANADA;
        probabilidadCierre = BigDecimal.valueOf(100);
        fechaCierre = fecha;
        fechaProximaAccion = null;
        motivoPerdida = null;
    }

    public void reabrirPorAnulacionContrato() {
        if (estado != EstadoOportunidad.GANADA) {
            throw new ReglaNegocioException(
                    "Solo una oportunidad ganada puede reabrirse por anulación"
            );
        }
        etapaActual = EtapaOportunidad.NEGOCIACION;
        estado = EstadoOportunidad.ABIERTA;
        probabilidadCierre = BigDecimal.valueOf(
                EtapaOportunidad.NEGOCIACION.getProbabilidadSugerida()
        );
        fechaCierre = null;
        fechaProximaAccion = null;
    }

    private void exigirAbierta() {
        if (estado != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException(
                    "La oportunidad debe estar abierta"
            );
        }
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

    public String getCodigo() {
        return codigo;
    }

    public Long getProspectoId() {
        return prospectoId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public Long getAsesorId() {
        return asesorId;
    }

    public EtapaOportunidad getEtapaActual() {
        return etapaActual;
    }

    public EstadoOportunidad getEstado() {
        return estado;
    }

    public BigDecimal getProbabilidadCierre() {
        return probabilidadCierre;
    }

    public BigDecimal getValorEstimado() {
        return valorEstimado;
    }

    public LocalDateTime getFechaUltimaActividad() {
        return fechaUltimaActividad;
    }

    public LocalDateTime getFechaProximaAccion() {
        return fechaProximaAccion;
    }

    public String getMotivoPerdida() {
        return motivoPerdida;
    }

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public Long getVersion() {
        return version;
    }
}
