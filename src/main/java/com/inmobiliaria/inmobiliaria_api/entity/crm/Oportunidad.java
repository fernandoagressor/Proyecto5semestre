package com.inmobiliaria.inmobiliaria_api.entity.crm;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Oportunidad comercial, guardada en MongoDB (rama 8).
 *
 * <p>Es un agregado con toda su vida dentro: los {@code lotesInteres}, el {@code historialEtapas},
 * las {@code actividades} y las {@code presentaciones} (estas últimas solo vienen de la migración).
 * Los montos se guardan como Decimal128.</p>
 *
 * <p>Los cambios que provoca la venta (cliente de la reserva, venta ganada por contrato,
 * reapertura por anulación) llegan desde MySQL por la cola {@code crm_pendientes} y se aplican con
 * los métodos {@code aplicar*}, que son idempotentes.</p>
 *
 * <p>{@code empresaId, proyectoId, asesorId, etapaActual, estado, probabilidadCierre,
 * valorEstimado, fechaCierre, creadoEn} son parte del contrato con {@code ProyectorOportunidades}.</p>
 */
@Document(collection = Oportunidad.COLECCION)
public class Oportunidad {

    public static final String COLECCION = "oportunidades";
    public static final int MAXIMO_LOTES_INTERES = 10;

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Id
    private Long id;
    private Long empresaId;
    private Long proyectoId;
    private String codigo;
    private Long prospectoId;
    private Long clienteId;
    private Long asesorId;
    private EtapaOportunidad etapaActual;
    private EstadoOportunidad estado;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal probabilidadCierre;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorEstimado;
    private LocalDateTime fechaUltimaActividad;
    private LocalDateTime fechaProximaAccion;
    private String motivoPerdida;
    private LocalDateTime fechaCierre;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private List<OportunidadLote> lotesInteres = new ArrayList<>();
    private List<OportunidadEtapaHistorial> historialEtapas = new ArrayList<>();
    private List<ActividadComercial> actividades = new ArrayList<>();
    private List<Map<String, Object>> presentaciones = new ArrayList<>();

    @Version
    private Long version;

    protected Oportunidad() {
    }

    public Oportunidad(
            Long empresaId,
            Long proyectoId,
            String codigo,
            Long prospectoId,
            Long asesorId,
            BigDecimal valorEstimado,
            LocalDateTime fechaProximaAccion
    ) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.codigo = codigo;
        this.prospectoId = prospectoId;
        this.asesorId = asesorId;
        this.etapaActual = EtapaOportunidad.PROSPECTO_RECIBIDO;
        this.estado = EstadoOportunidad.ABIERTA;
        this.probabilidadCierre = BigDecimal.valueOf(
                etapaActual.getProbabilidadSugerida()
        );
        this.creadoEn = ahora();
        this.actualizadoEn = creadoEn;
        actualizarDatos(valorEstimado, fechaProximaAccion);
    }

    public void actualizarDatos(
            BigDecimal valorEstimado,
            LocalDateTime fechaProximaAccion
    ) {
        exigirAbierta();
        if (valorEstimado != null && valorEstimado.signum() < 0) {
            throw new ReglaNegocioException(
                    "El valor estimado no puede ser negativo"
            );
        }
        this.valorEstimado = valorEstimado;
        this.fechaProximaAccion = fechaProximaAccion;
    }

    public EtapaOportunidad cambiarEtapa(
            EtapaOportunidad nuevaEtapa,
            BigDecimal nuevaProbabilidad
    ) {
        exigirAbierta();
        if (nuevaEtapa == etapaActual) {
            throw new ReglaNegocioException(
                    "La nueva etapa debe ser diferente a la actual"
            );
        }
        BigDecimal probabilidad = nuevaProbabilidad == null
                ? BigDecimal.valueOf(nuevaEtapa.getProbabilidadSugerida())
                : nuevaProbabilidad;
        if (probabilidad.signum() < 0
                || probabilidad.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ReglaNegocioException(
                    "La probabilidad debe estar entre 0 y 100"
            );
        }
        EtapaOportunidad anterior = etapaActual;
        etapaActual = nuevaEtapa;
        probabilidadCierre = probabilidad;
        return anterior;
    }

    public void registrarActividad(
            LocalDateTime fechaActividad,
            LocalDateTime proximaAccion
    ) {
        exigirAbierta();
        fechaUltimaActividad = fechaActividad;
        fechaProximaAccion = proximaAccion;
    }

    public void cerrarPerdida(String motivo, LocalDateTime fecha) {
        exigirAbierta();
        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException(
                    "Debe indicar el motivo de pérdida"
            );
        }
        estado = EstadoOportunidad.PERDIDA;
        motivoPerdida = motivo;
        fechaCierre = fecha;
        probabilidadCierre = BigDecimal.ZERO;
        fechaProximaAccion = null;
    }

    public void reabrir() {
        if (estado != EstadoOportunidad.PERDIDA
                && estado != EstadoOportunidad.CANCELADA) {
            throw new ReglaNegocioException(
                    "Solo se pueden reabrir oportunidades perdidas o canceladas"
            );
        }
        estado = EstadoOportunidad.ABIERTA;
        motivoPerdida = null;
        fechaCierre = null;
        probabilidadCierre = BigDecimal.valueOf(
                etapaActual.getProbabilidadSugerida()
        );
    }

    public void vincularCliente(Long nuevoClienteId) {
        exigirAbierta();
        if (nuevoClienteId == null) {
            throw new ReglaNegocioException("El cliente es obligatorio");
        }
        if (clienteId != null && !clienteId.equals(nuevoClienteId)) {
            throw new ReglaNegocioException(
                    "La oportunidad ya está vinculada a otro cliente"
            );
        }
        clienteId = nuevoClienteId;
    }

    public void ganarPorContrato(LocalDateTime fecha) {
        exigirAbierta();
        etapaActual = EtapaOportunidad.CIERRE;
        estado = EstadoOportunidad.GANADA;
        probabilidadCierre = BigDecimal.valueOf(100);
        fechaCierre = fecha;
        fechaProximaAccion = null;
        motivoPerdida = null;
    }

    public void reabrirPorAnulacionContrato() {
        if (estado != EstadoOportunidad.GANADA) {
            throw new ReglaNegocioException(
                    "Solo una oportunidad ganada puede reabrirse por anulación"
            );
        }
        etapaActual = EtapaOportunidad.NEGOCIACION;
        estado = EstadoOportunidad.ABIERTA;
        probabilidadCierre = BigDecimal.valueOf(
                EtapaOportunidad.NEGOCIACION.getProbabilidadSugerida()
        );
        fechaCierre = null;
        fechaProximaAccion = null;
    }

    /** Asigna el identificador de una oportunidad nueva. Solo puede hacerse una vez. */
    public void identificar(long nuevoId) {
        if (id != null) {
            throw new IllegalStateException("La oportunidad ya tiene identificador " + id);
        }
        id = nuevoId;
    }

    /** Marca la fecha de la última modificación; lo llama el repositorio antes de guardar. */
    public void tocar() {
        actualizadoEn = ahora();
    }

    // --- Lotes de interés -----------------------------------------------------------------

    public boolean tieneLote(Long loteId) {
        return loteId != null && lotesInteres.stream().anyMatch(lote -> loteId.equals(lote.getLoteId()));
    }

    public OportunidadLote agregarLote(long relacionId, Long loteId, int prioridad) {
        exigirAbierta();
        if (lotesInteres.size() >= MAXIMO_LOTES_INTERES) {
            throw new ReglaNegocioException("Una oportunidad no puede tener más de diez lotes de interés");
        }
        if (tieneLote(loteId)) {
            throw new ReglaNegocioException("El lote ya está vinculado a la oportunidad");
        }
        OportunidadLote relacion = new OportunidadLote(relacionId, id, loteId, prioridad, ahora());
        lotesInteres.add(relacion);
        return relacion;
    }

    public void quitarLote(Long loteId) {
        exigirAbierta();
        lotesInteres.remove(lote(loteId));
    }

    /** Marca el lote como seleccionado (y desmarca los demás) o lo desmarca. */
    public OportunidadLote seleccionarLote(Long loteId, boolean seleccionado) {
        exigirAbierta();
        OportunidadLote objetivo = lote(loteId);
        if (seleccionado) {
            lotesInteres.forEach(relacion -> relacion.seleccionar(relacion == objetivo));
        } else {
            objetivo.seleccionar(false);
        }
        return objetivo;
    }

    public Optional<OportunidadLote> buscarLote(Long loteId) {
        return lotesInteres.stream().filter(lote -> lote.getLoteId().equals(loteId)).findFirst();
    }

    public List<OportunidadLote> getLotesInteres() {
        return lotesInteres.stream()
                .sorted(Comparator.comparingInt(OportunidadLote::getPrioridad)
                        .thenComparing(OportunidadLote::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder())))
                .toList();
    }

    private OportunidadLote lote(Long loteId) {
        return buscarLote(loteId)
                .orElseThrow(() -> new ReglaNegocioException("El lote no está vinculado a la oportunidad"));
    }

    // --- Historial y actividades ------------------------------------------------------------

    public OportunidadEtapaHistorial registrarEtapa(long historialId, EtapaOportunidad anterior,
                                                     EtapaOportunidad nueva, Long usuarioId, String observacion) {
        OportunidadEtapaHistorial cambio = new OportunidadEtapaHistorial(historialId, id, anterior, nueva,
                usuarioId, observacion, ahora());
        historialEtapas.add(cambio);
        return cambio;
    }

    public List<OportunidadEtapaHistorial> getHistorialEtapas() {
        return historialEtapas.stream()
                .sorted(Comparator.comparing(OportunidadEtapaHistorial::getCreadoEn,
                                Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
                        .thenComparing(OportunidadEtapaHistorial::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder())))
                .toList();
    }

    /** Registra la actividad y actualiza las fechas de seguimiento de la oportunidad. */
    public ActividadComercial agregarActividad(long actividadId, TipoActividadComercial tipo, String resultado,
                                               String descripcion, LocalDateTime fechaActividad,
                                               String proximaAccion, LocalDateTime fechaProximaAccionNueva) {
        registrarActividad(fechaActividad, fechaProximaAccionNueva);
        ActividadComercial actividad = new ActividadComercial(actividadId, id, asesorId, tipo, resultado,
                descripcion, fechaActividad, proximaAccion, fechaProximaAccionNueva, ahora());
        actividades.add(actividad);
        return actividad;
    }

    /** Actividades de la más reciente a la más antigua. */
    public List<ActividadComercial> getActividades() {
        return actividades.stream()
                .sorted(Comparator.comparing(ActividadComercial::getFechaActividad,
                                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(ActividadComercial::getId, Comparator.nullsLast(Comparator.<Long>reverseOrder())))
                .toList();
    }

    // --- Cambios que llegan desde la venta (cola crm_pendientes) ------------------------------

    /** Valida, sin modificar, que la oportunidad pueda reservarse para el cliente. */
    public void validarReserva(Long nuevoClienteId) {
        exigirAbierta();
        if (nuevoClienteId == null) {
            throw new ReglaNegocioException("El cliente es obligatorio");
        }
        if (clienteId != null && !clienteId.equals(nuevoClienteId)) {
            throw new ReglaNegocioException("La oportunidad ya está vinculada a otro cliente");
        }
    }

    /** Vincula el cliente de una reserva ya confirmada. @return si hubo cambios */
    public boolean aplicarClienteDeReserva(Long nuevoClienteId) {
        if (nuevoClienteId == null || nuevoClienteId.equals(clienteId)) {
            return false;
        }
        if (clienteId != null) {
            throw new ReglaNegocioException("La oportunidad ya está vinculada a otro cliente");
        }
        clienteId = nuevoClienteId;
        return true;
    }

    /**
     * Marca la venta ganada por un contrato ya firmado. La firma manda: se aplica aunque la
     * oportunidad se haya cerrado entretanto. Si ya estaba ganada no hace nada.
     *
     * @return si hubo cambios
     */
    public boolean aplicarVentaGanada(LocalDateTime fecha, long historialId, Long usuarioId, Long contratoId) {
        if (estado == EstadoOportunidad.GANADA) {
            return false;
        }
        EtapaOportunidad anterior = etapaActual;
        etapaActual = EtapaOportunidad.CIERRE;
        estado = EstadoOportunidad.GANADA;
        probabilidadCierre = BigDecimal.valueOf(100);
        fechaCierre = fecha;
        fechaProximaAccion = null;
        motivoPerdida = null;
        registrarEtapa(historialId, anterior, etapaActual, usuarioId, "Venta confirmada por contrato " + contratoId);
        return true;
    }

    /** Reabre la venta por anulación del contrato. Si no estaba ganada no hace nada. @return si hubo cambios */
    public boolean aplicarAnulacionVenta(long historialId, Long usuarioId, Long contratoId) {
        if (estado != EstadoOportunidad.GANADA) {
            return false;
        }
        EtapaOportunidad anterior = etapaActual;
        reabrirPorAnulacionContrato();
        registrarEtapa(historialId, anterior, etapaActual, usuarioId,
                "Venta reabierta por anulación del contrato " + contratoId);
        return true;
    }

    private void exigirAbierta() {
        if (estado != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException(
                    "La oportunidad debe estar abierta"
            );
        }
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

    public Long getProspectoId() {
        return prospectoId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public Long getAsesorId() {
        return asesorId;
    }

    public EtapaOportunidad getEtapaActual() {
        return etapaActual;
    }

    public EstadoOportunidad getEstado() {
        return estado;
    }

    public BigDecimal getProbabilidadCierre() {
        return probabilidadCierre;
    }

    public BigDecimal getValorEstimado() {
        return valorEstimado;
    }

    public LocalDateTime getFechaUltimaActividad() {
        return fechaUltimaActividad;
    }

    public LocalDateTime getFechaProximaAccion() {
        return fechaProximaAccion;
    }

    public String getMotivoPerdida() {
        return motivoPerdida;
    }

    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
