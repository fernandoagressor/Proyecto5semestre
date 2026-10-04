package com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contrato_versiones")
public class ContratoVersion extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contrato_id", nullable = false)
    private Long contratoId;

    @Column(name = "numero_version", nullable = false)
    private int numeroVersion;

    @Column(name = "archivo_id")
    private Long archivoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_json", nullable = false, columnDefinition = "json")
    private JsonNode datosJson;

    @Column(nullable = false, length = 400)
    private String motivo;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(nullable = false)
    private boolean vigente;

    protected ContratoVersion() {
    }

    public ContratoVersion(
            Long contratoId,
            int numeroVersion,
            Long archivoId,
            JsonNode datosJson,
            String motivo,
            Long creadoPor
    ) {
        this.contratoId = contratoId;
        this.numeroVersion = numeroVersion;
        this.archivoId = archivoId;
        this.datosJson = datosJson;
        this.motivo = motivo;
        this.creadoPor = creadoPor;
        this.vigente = true;
    }

    public void desactivar() {
        vigente = false;
    }

    public Long getId() { return id; }
    public int getNumeroVersion() { return numeroVersion; }
    public Long getArchivoId() { return archivoId; }
    public JsonNode getDatosJson() { return datosJson; }
    public String getMotivo() { return motivo; }
    public Long getCreadoPor() { return creadoPor; }
    public boolean isVigente() { return vigente; }
}
