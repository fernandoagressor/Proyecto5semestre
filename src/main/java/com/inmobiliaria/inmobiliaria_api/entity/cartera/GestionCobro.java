package com.inmobiliaria.inmobiliaria_api.entity.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Gestión de cobro (llamada, visita, mensaje) sobre un contrato, guardada en MongoDB (rama 9).
 * Es un registro de bitácora: no se modifica después de creado. Guarda copia del proyecto del
 * contrato para que el dashboard de cartera filtre sin cruzar bases.
 */
@Document(collection = GestionCobro.COLECCION)
public class GestionCobro {
    public static final String COLECCION = "gestiones_cobro";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private Long contratoId;
    private Long cuotaId;
    private Long gestorId;
    private CanalCobro canal;
    private String resultado;
    private String detalle;
    private LocalDate promesaPagoFecha;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal promesaPagoValor;
    private LocalDateTime fechaGestion;
    private LocalDateTime creadoEn;

    protected GestionCobro() {
    }

    public GestionCobro(Long empresaId, Long contratoId, Long cuotaId, Long gestorId, CanalCobro canal, String resultado, String detalle, LocalDate promesaFecha, BigDecimal promesaValor, LocalDateTime fechaGestion) {
        if ((promesaFecha == null) != (promesaValor == null)) {
            throw new ReglaNegocioException("La promesa requiere fecha y valor");
        }
        if (promesaValor != null && promesaValor.signum() <= 0) {
            throw new ReglaNegocioException("El valor prometido debe ser positivo");
        }
        this.empresaId = empresaId;
        this.contratoId = contratoId;
        this.cuotaId = cuotaId;
        this.gestorId = gestorId;
        this.canal = canal;
        this.resultado = resultado;
        this.detalle = detalle;
        this.promesaPagoFecha = promesaFecha;
        this.promesaPagoValor = promesaValor;
        this.fechaGestion = fechaGestion;
        this.creadoEn = LocalDateTime.now(BOGOTA);
    }

    /** Asigna el identificador y el proyecto del contrato a una gestión nueva. */
    public void identificar(long nuevoId, Long proyecto) {
        if (id != null) {
            throw new IllegalStateException("La gestión ya tiene identificador " + id);
        }
        this.id = nuevoId;
        this.proyectoId = proyecto;
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public Long getContratoId() { return contratoId; }
    public Long getCuotaId() { return cuotaId; }
    public Long getGestorId() { return gestorId; }
    public CanalCobro getCanal() { return canal; }
    public String getResultado() { return resultado; }
    public String getDetalle() { return detalle; }
    public LocalDate getPromesaPagoFecha() { return promesaPagoFecha; }
    public BigDecimal getPromesaPagoValor() { return promesaPagoValor; }
    public LocalDateTime getFechaGestion() { return fechaGestion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
