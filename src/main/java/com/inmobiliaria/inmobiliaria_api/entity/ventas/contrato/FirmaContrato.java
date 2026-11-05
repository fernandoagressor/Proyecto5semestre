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

    /** Archivo firmado que respalda esta firma (contrato firmado subido en la oficina o en el portal). */
    @Column(name = "archivo_id")
    private Long archivoId;

    /** OFICINA o PORTAL; null en las firmas registradas antes de V34 o por proveedor externo. */
    @Column(length = 20)
    private String origen;

    @Column(name = "motivo_rechazo", length = 400)
    private String motivoRechazo;

    @Column(name = "revisada_por")
    private Long revisadaPor;

    @Column(name = "revisada_en")
    private LocalDateTime revisadaEn;

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

    /** Asocia el archivo firmado y de dónde llegó (OFICINA o PORTAL). */
    public FirmaContrato conArchivo(Long archivoId, String origen) {
        this.archivoId = archivoId;
        this.origen = origen;
        return this;
    }

    /** Revisión de una firma que llegó por el portal: válida o rechazada con motivo. */
    public void revisar(boolean valida, String motivo, Long usuarioId, LocalDateTime fecha) {
        if (estadoValidacion != EstadoValidacionFirma.PENDIENTE) {
            throw new com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException(
                    "Esta firma ya fue revisada");
        }
        estadoValidacion = valida ? EstadoValidacionFirma.VALIDA : EstadoValidacionFirma.INVALIDA;
        motivoRechazo = valida ? null : motivo;
        revisadaPor = usuarioId;
        revisadaEn = fecha;
    }

    public Long getContratoFirmanteId() { return contratoFirmanteId; }
    public Long getArchivoId() { return archivoId; }
    public String getOrigen() { return origen; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public LocalDateTime getRevisadaEn() { return revisadaEn; }
    public Long getId() { return id; }
    public MetodoFirma getMetodo() { return metodo; }
    public String getProveedor() { return proveedor; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public String getHashDocumento() { return hashDocumento; }
    public String getIpOrigen() { return ipOrigen; }
    public LocalDateTime getFirmadaEn() { return firmadaEn; }
    public EstadoValidacionFirma getEstadoValidacion() { return estadoValidacion; }
}
