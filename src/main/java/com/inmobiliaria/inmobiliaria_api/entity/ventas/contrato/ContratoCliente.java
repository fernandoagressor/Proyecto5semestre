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
import java.math.BigDecimal;

@Entity
@Table(name = "contrato_clientes")
public class ContratoCliente extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_en_contrato", nullable = false, length = 25)
    private RolContratoCliente rolEnContrato;

    @Column(name = "porcentaje_participacion", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeParticipacion;

    protected ContratoCliente() {
    }

    public ContratoCliente(
            Long contratoId,
            Long clienteId,
            RolContratoCliente rolEnContrato,
            BigDecimal porcentajeParticipacion
    ) {
        this.contratoId = contratoId;
        this.clienteId = clienteId;
        this.rolEnContrato = rolEnContrato;
        this.porcentajeParticipacion = porcentajeParticipacion;
    }

    public void ajustarParticipacion(BigDecimal nuevoPorcentaje) {
        if (nuevoPorcentaje == null
                || nuevoPorcentaje.signum() < 0
                || nuevoPorcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ReglaNegocioException(
                    "La participación debe estar entre 0 y 100"
            );
        }
        porcentajeParticipacion = nuevoPorcentaje;
    }

    public Long getId() { return id; }
    public Long getContratoId() { return contratoId; }
    public Long getClienteId() { return clienteId; }
    public RolContratoCliente getRolEnContrato() { return rolEnContrato; }
    public BigDecimal getPorcentajeParticipacion() { return porcentajeParticipacion; }
}
