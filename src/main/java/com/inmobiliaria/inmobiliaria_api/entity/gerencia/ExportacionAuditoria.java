package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Solicitud de exportación de evidencia de auditoría, guardada en MongoDB (rama 7). */
@Document(collection = ExportacionAuditoria.COLECCION)
public class ExportacionAuditoria {

    public static final String COLECCION = "exportaciones_auditoria";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long usuarioId;
    private String filtrosJson;
    private String formato;
    private Long archivoId;
    private String motivo;
    private String estado;
    private LocalDateTime expiraEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    protected ExportacionAuditoria() { }

    public ExportacionAuditoria(Long id, Long empresaId, Long usuarioId, String filtrosJson,
                                String formato, String motivo, LocalDateTime expiraEn) {
        this.id = id;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
        this.filtrosJson = filtrosJson;
        this.formato = formato;
        this.motivo = motivo;
        this.estado = "PENDIENTE";
        this.expiraEn = expiraEn;
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getFiltrosJson() { return filtrosJson; }
    public String getFormato() { return formato; }
    public Long getArchivoId() { return archivoId; }
    public String getMotivo() { return motivo; }
    public String getEstado() { return estado; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
