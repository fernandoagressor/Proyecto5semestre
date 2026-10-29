package com.inmobiliaria.inmobiliaria_api.entity.simulacion;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Simulación de financiación de un lote, guardada en MongoDB (rama 8b).
 *
 * <p>El cronograma ({@code cuotas}) vive embebido: se genera una vez y se lee completo. Guarda
 * copia del asesor de la oportunidad para filtrar por alcance sin cruzar colecciones. Los montos
 * se guardan como Decimal128.</p>
 *
 * <p>La aceptación la provoca la reserva (MySQL): llega por la cola {@code crm_pendientes} y se
 * aplica con {@link #aplicarAceptacion()}, que es idempotente.</p>
 */
@Document(collection = Simulacion.COLECCION)
public class Simulacion {

    public static final String COLECCION = "simulaciones";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private String codigo;
    private Long oportunidadId;
    private Long asesorId;
    private Long loteId;
    private Long creadoPor;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorLote;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal descuento;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal cuotaInicial;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal saldoFinanciar;
    private int numeroCuotas;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal tasaEfectivaAnual;
    private Periodicidad periodicidad;
    private LocalDateTime vigenteHasta;
    private EstadoSimulacion estado;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private List<SimulacionCuota> cuotas = new ArrayList<>();

    @Version
    private Long version;

    protected Simulacion() {
    }

    public Simulacion(
            Long empresaId,
            Long proyectoId,
            String codigo,
            Long oportunidadId,
            Long loteId,
            Long creadoPor,
            BigDecimal valorLote,
            BigDecimal descuento,
            BigDecimal cuotaInicial,
            BigDecimal saldoFinanciar,
            int numeroCuotas,
            BigDecimal tasaEfectivaAnual,
            Periodicidad periodicidad,
            LocalDateTime vigenteHasta
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.oportunidadId = oportunidadId;
        this.loteId = loteId;
        this.creadoPor = creadoPor;
        this.valorLote = valorLote;
        this.descuento = descuento;
        this.cuotaInicial = cuotaInicial;
        this.saldoFinanciar = saldoFinanciar;
        this.numeroCuotas = numeroCuotas;
        this.tasaEfectivaAnual = tasaEfectivaAnual;
        this.periodicidad = periodicidad;
        this.vigenteHasta = vigenteHasta;
        this.estado = EstadoSimulacion.BORRADOR;
        this.creadoEn = LocalDateTime.now(BOGOTA);
        this.actualizadoEn = creadoEn;
    }

    public void enviar(LocalDateTime ahora) {
        if (estado != EstadoSimulacion.BORRADOR) {
            throw new ReglaNegocioException(
                    "Solo una simulación en borrador puede enviarse"
            );
        }
        if (!vigenteHasta.isAfter(ahora)) {
            throw new ReglaNegocioException(
                    "No se puede enviar una simulación vencida"
            );
        }
        estado = EstadoSimulacion.ENVIADA;
    }

    public void cancelar() {
        if (estado == EstadoSimulacion.ACEPTADA
                || estado == EstadoSimulacion.CANCELADA) {
            throw new ReglaNegocioException(
                    "La simulación no admite cancelación en su estado actual"
            );
        }
        estado = EstadoSimulacion.CANCELADA;
    }

    public void aceptar(LocalDateTime ahora) {
        validarAceptacion(ahora);
        estado = EstadoSimulacion.ACEPTADA;
    }

    /** Valida, sin modificar, que la simulación pueda aceptarse en una reserva. */
    public void validarAceptacion(LocalDateTime ahora) {
        if (estado != EstadoSimulacion.ENVIADA) {
            throw new ReglaNegocioException(
                    "Solo una simulación enviada puede aceptarse"
            );
        }
        if (!vigenteHasta.isAfter(ahora)) {
            throw new ReglaNegocioException(
                    "La simulación está vencida"
            );
        }
    }

    /**
     * Marca la simulación aceptada por una reserva ya confirmada. La reserva manda: se aplica
     * aunque la simulación haya vencido o se haya cancelado entretanto.
     *
     * @return si hubo cambios
     */
    public boolean aplicarAceptacion() {
        if (estado == EstadoSimulacion.ACEPTADA) {
            return false;
        }
        estado = EstadoSimulacion.ACEPTADA;
        return true;
    }

    /** Asigna el identificador de una simulación nueva. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La simulación ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Copia del asesor de la oportunidad, para filtrar por alcance. */
    public void asignarAsesor(Long asesor) {
        this.asesorId = asesor;
    }

    /** Guarda el cronograma generado; solo se asigna una vez. */
    public void asignarCronograma(List<SimulacionCuota> cronograma) {
        if (!cuotas.isEmpty()) {
            throw new IllegalStateException("La simulación ya tiene cronograma");
        }
        cuotas = new ArrayList<>(cronograma);
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = LocalDateTime.now(BOGOTA);
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

    public Long getOportunidadId() {
        return oportunidadId;
    }

    public Long getLoteId() {
        return loteId;
    }

    public Long getCreadoPor() {
        return creadoPor;
    }

    public BigDecimal getValorLote() {
        return valorLote;
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public BigDecimal getCuotaInicial() {
        return cuotaInicial;
    }

    public BigDecimal getSaldoFinanciar() {
        return saldoFinanciar;
    }

    public int getNumeroCuotas() {
        return numeroCuotas;
    }

    public BigDecimal getTasaEfectivaAnual() {
        return tasaEfectivaAnual;
    }

    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    public LocalDateTime getVigenteHasta() {
        return vigenteHasta;
    }

    public EstadoSimulacion getEstado() {
        return estado;
    }

    public Long getVersion() {
        return version;
    }

    public Long getAsesorId() {
        return asesorId;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    /** Cronograma en orden de cuota. */
    public List<SimulacionCuota> getCuotas() {
        return cuotas.stream().sorted(Comparator.comparingInt(SimulacionCuota::getNumero)).toList();
    }
}
