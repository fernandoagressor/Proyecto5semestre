package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Estado de cuenta emitido con corte histórico, guardado en MongoDB (rama 9). Es inmutable y hay
 * uno solo por contrato y fecha de corte (índice único).
 */
@Document(collection = EstadoCuentaGenerado.COLECCION)
public class EstadoCuentaGenerado {
    public static final String COLECCION = "estados_cuenta";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long contratoId;
    private LocalDate fechaCorte;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal saldoCapital;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal saldoMora;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal saldoTotal;
    private Long archivoId;
    private Long generadoPor;
    private LocalDateTime creadoEn;

    protected EstadoCuentaGenerado() {
    }

    public EstadoCuentaGenerado(Long empresaId, Long contratoId, LocalDate fechaCorte, BigDecimal saldoCapital, BigDecimal saldoMora, Long archivoId, Long generadoPor) {
        this.empresaId = empresaId;
        this.contratoId = contratoId;
        this.fechaCorte = fechaCorte;
        this.saldoCapital = saldoCapital;
        this.saldoMora = saldoMora;
        this.saldoTotal = saldoCapital.add(saldoMora);
        this.archivoId = archivoId;
        this.generadoPor = generadoPor;
        this.creadoEn = LocalDateTime.now(BOGOTA);
    }

    /** Asigna el identificador de un estado de cuenta nuevo. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("El estado de cuenta ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getContratoId() { return contratoId; }
    public LocalDate getFechaCorte() { return fechaCorte; }
    public BigDecimal getSaldoCapital() { return saldoCapital; }
    public BigDecimal getSaldoMora() { return saldoMora; }
    public BigDecimal getSaldoTotal() { return saldoTotal; }
    public Long getArchivoId() { return archivoId; }
    public Long getGeneradoPor() { return generadoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
