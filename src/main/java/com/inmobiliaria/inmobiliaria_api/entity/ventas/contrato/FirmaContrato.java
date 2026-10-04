package com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "firmas_contrato")
public class FirmaContrato extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contrato_firmante_id", nullable = false)
    private Long contratoFirmanteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private MetodoFirma metodo;

    @Column(length = 100)
    private String proveedor;

    @Column(name = "referencia_externa", length = 180)
    private String referenciaExterna;

    @Column(name = "hash_documento", nullable = false, length = 64)
    private String hashDocumento;

    @Column(name = "ip_origen", length = 64)
    private String ipOrigen;

    @Column(name = "firmada_en", nullable = false)
    private LocalDateTime firmadaEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacion", nullable = false, length = 20)
    private EstadoValidacionFirma estadoValidacion;

    protected FirmaContrato() {
    }

    public FirmaContrato(
            Long contratoFirmanteId,
            MetodoFirma metodo,
            String proveedor,
            String referenciaExterna,
            String hashDocumento,
            String ipOrigen,
            LocalDateTime firmadaEn,
            EstadoValidacionFirma estadoValidacion
    ) {
        this.contratoFirmanteId = contratoFirmanteId;
        this.metodo = metodo;
        this.proveedor = proveedor;
        this.referenciaExterna = referenciaExterna;
        this.hashDocumento = hashDocumento;
        this.ipOrigen = ipOrigen;
        this.firmadaEn = firmadaEn;
        this.estadoValidacion = estadoValidacion;
    }

    public Long getId() { return id; }
    public MetodoFirma getMetodo() { return metodo; }
    public String getProveedor() { return proveedor; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public String getHashDocumento() { return hashDocumento; }
    public String getIpOrigen() { return ipOrigen; }
    public LocalDateTime getFirmadaEn() { return firmadaEn; }
    public EstadoValidacionFirma getEstadoValidacion() { return estadoValidacion; }
}
