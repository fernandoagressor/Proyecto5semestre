package com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "contrato_firmantes")
public class ContratoFirmante extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_firmante", nullable = false, length = 25)
    private TipoFirmante tipoFirmante;

    @Column(name = "orden_firma", nullable = false)
    private int ordenFirma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFirmante estado;

    @Column(name = "fecha_programada")
    private LocalDateTime fechaProgramada;

    @Column(name = "fecha_firma")
    private LocalDateTime fechaFirma;

    @Column(name = "archivo_firma_id")
    private Long archivoFirmaId;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ContratoFirmante() {
    }

    public ContratoFirmante(
            Long contratoId,
            Long personaId,
            TipoFirmante tipoFirmante,
            int ordenFirma,
            LocalDateTime fechaProgramada
    ) {
        if (ordenFirma <= 0) {
            throw new ReglaNegocioException(
                    "El orden de firma debe ser mayor que cero"
            );
        }
        this.contratoId = contratoId;
        this.personaId = personaId;
        this.tipoFirmante = tipoFirmante;
        this.ordenFirma = ordenFirma;
        this.fechaProgramada = fechaProgramada;
        this.estado = EstadoFirmante.PENDIENTE;
    }

    public void marcarEnviada(LocalDateTime fecha) {
        if (estado != EstadoFirmante.PENDIENTE) {
            throw new ReglaNegocioException(
                    "La solicitud de firma ya fue procesada"
            );
        }
        estado = EstadoFirmante.ENVIADA;
        if (fechaProgramada == null) {
            fechaProgramada = fecha;
        }
    }

    public void registrarFirma(
            LocalDateTime fecha,
            Long archivoId,
            EstadoValidacionFirma validacion
    ) {
        if (estado != EstadoFirmante.PENDIENTE
                && estado != EstadoFirmante.ENVIADA) {
            throw new ReglaNegocioException(
                    "El firmante no admite una nueva firma"
            );
        }
        if (validacion == EstadoValidacionFirma.INVALIDA) {
            estado = EstadoFirmante.RECHAZADA;
            return;
        }
        if (validacion == EstadoValidacionFirma.VALIDA) {
            estado = EstadoFirmante.FIRMADA;
            fechaFirma = fecha;
            archivoFirmaId = archivoId;
        } else {
            estado = EstadoFirmante.ENVIADA;
        }
    }

    public Long getId() { return id; }
    public Long getContratoId() { return contratoId; }
    public Long getPersonaId() { return personaId; }
    public TipoFirmante getTipoFirmante() { return tipoFirmante; }
    public int getOrdenFirma() { return ordenFirma; }
    public EstadoFirmante getEstado() { return estado; }
    public LocalDateTime getFechaProgramada() { return fechaProgramada; }
    public LocalDateTime getFechaFirma() { return fechaFirma; }
    public Long getArchivoFirmaId() { return archivoFirmaId; }
    public Long getVersion() { return version; }
}
