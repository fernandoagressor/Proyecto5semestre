package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Solicitud de reporte operativo, ejecutivo o financiero, guardada en MongoDB (rama 7).
 * El archivo generado sigue en la tabla {@code archivos} de MySQL.
 */
@Document(collection = ReporteGenerado.COLECCION)
public class ReporteGenerado {

    public static final String COLECCION = "reportes_generados";
    public static final String PENDIENTE = "PENDIENTE";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private Long solicitadoPor;
    private String tipoReporte;
    private String filtrosJson;
    private String formato;
    private Long archivoId;
    private String estado;
    private LocalDateTime generadoEn;
    private LocalDateTime expiraEn;
    private String detalleError;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    @Version
    private Long version;

    protected ReporteGenerado() { }

    public ReporteGenerado(Long id, Long empresaId, Long proyectoId, Long solicitadoPor,
                           String tipoReporte, String filtrosJson, String formato, LocalDateTime expiraEn) {
        this.id = id;
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.solicitadoPor = solicitadoPor;
        this.tipoReporte = tipoReporte;
        this.filtrosJson = filtrosJson;
        this.formato = formato;
        this.estado = PENDIENTE;
        this.expiraEn = expiraEn;
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    /**
     * Completa el reporte: con archivo queda GENERADO; sin archivo queda FALLIDO y exige el
     * detalle del error.
     */
    public void completar(Long archivo, String error, LocalDateTime fecha) {
        if (!PENDIENTE.equals(estado)) {
            throw new ReglaNegocioException("Solo un reporte pendiente puede completarse");
        }
        boolean exito = archivo != null;
        if (!exito && (error == null || error.isBlank())) {
            throw new ReglaNegocioException("Un reporte fallido requiere el detalle del error");
        }
        archivoId = archivo;
        estado = exito ? "GENERADO" : "FALLIDO";
        generadoEn = fecha;
        detalleError = exito ? null : error.trim();
        actualizadoEn = LocalDateTime.now(BOGOTA);
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getSolicitadoPor() { return solicitadoPor; }
    public String getTipoReporte() { return tipoReporte; }
    public String getFiltrosJson() { return filtrosJson; }
    public String getFormato() { return formato; }
    public Long getArchivoId() { return archivoId; }
    public String getEstado() { return estado; }
    public LocalDateTime getGeneradoEn() { return generadoEn; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public String getDetalleError() { return detalleError; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public Long getVersion() { return version; }
}
