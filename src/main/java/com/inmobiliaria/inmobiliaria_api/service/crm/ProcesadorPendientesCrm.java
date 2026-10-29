package com.inmobiliaria.inmobiliaria_api.service.crm;

import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoProspecto;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Prospecto;
import com.inmobiliaria.inmobiliaria_api.repository.crm.OportunidadRepository;
import com.inmobiliaria.inmobiliaria_api.repository.crm.ProspectoRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aplica en MongoDB los pendientes de {@code crm_pendientes} (ver {@link PendientesCrmService}).
 *
 * <ul>
 *   <li>Se llama justo después del commit de la venta y, como respaldo, cada
 *       {@code app.crm.pendientes.intervalo} (30 s por defecto) para lo que haya quedado sin aplicar.</li>
 *   <li>Cada pendiente se "toma" por {@value #LEASE_SEGUNDOS} segundos con un UPDATE condicional, así
 *       dos instancias (o el reintento y el disparo inmediato) nunca lo aplican a la vez.</li>
 *   <li>Respeta el orden por oportunidad: si un pendiente falla, los siguientes de esa misma
 *       oportunidad esperan al próximo intento.</li>
 *   <li>Los cambios son idempotentes: aplicar dos veces el mismo pendiente no cambia el resultado.</li>
 *   <li>Tras {@value #MAX_INTENTOS} intentos fallidos el pendiente se deja de reintentar y queda con
 *       su último error para revisión manual.</li>
 * </ul>
 */
@Component
public class ProcesadorPendientesCrm {

    static final int MAX_INTENTOS = 20;
    static final int LEASE_SEGUNDOS = 60;
    private static final int LOTE = 100;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Logger log = LoggerFactory.getLogger(ProcesadorPendientesCrm.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate nuevaTransaccion;
    private final OportunidadRepository oportunidades;
    private final ProspectoRepository prospectos;
    private final SecuenciaService secuencias;

    public ProcesadorPendientesCrm(JdbcTemplate jdbcTemplate,
                                   PlatformTransactionManager transactionManager,
                                   OportunidadRepository oportunidades,
                                   ProspectoRepository prospectos,
                                   SecuenciaService secuencias) {
        this.jdbcTemplate = jdbcTemplate;
        this.nuevaTransaccion = new TransactionTemplate(transactionManager);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.oportunidades = oportunidades;
        this.prospectos = prospectos;
        this.secuencias = secuencias;
    }

    /** Reintento periódico de todo lo pendiente. */
    @Scheduled(fixedDelayString = "${app.crm.pendientes.intervalo:PT30S}",
            initialDelayString = "${app.crm.pendientes.espera-inicial:PT20S}")
    public void procesarPendientes() {
        procesar(leer("procesado_en IS NULL AND intentos < ?", MAX_INTENTOS));
    }

    /** Aplica lo pendiente de una oportunidad; se llama justo después del commit de la venta. */
    public void procesarOportunidad(Long oportunidadId) {
        try {
            procesar(leer("procesado_en IS NULL AND intentos < ? AND oportunidad_id = ?", MAX_INTENTOS, oportunidadId));
        } catch (RuntimeException e) {
            log.warn("No se pudieron aplicar los pendientes de la oportunidad {}; se reintentarán", oportunidadId, e);
        }
    }

    private void procesar(List<Pendiente> pendientes) {
        Set<Long> bloqueadas = new HashSet<>();
        for (Pendiente pendiente : pendientes) {
            if (bloqueadas.contains(pendiente.oportunidadId()) || !tomar(pendiente.id())) {
                bloqueadas.add(pendiente.oportunidadId());
                continue;
            }
            try {
                aplicar(pendiente);
                marcar("procesado_en = ?, tomado_hasta = NULL, ultimo_error = NULL", pendiente.id(), ahora());
            } catch (RuntimeException e) {
                bloqueadas.add(pendiente.oportunidadId());
                log.warn("Falló el pendiente CRM {} ({}) de la oportunidad {}", pendiente.id(), pendiente.tipo(),
                        pendiente.oportunidadId(), e);
                marcar("intentos = intentos + 1, tomado_hasta = NULL, ultimo_error = ?", pendiente.id(),
                        recortar(e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    private void aplicar(Pendiente p) {
        Oportunidad oportunidad = oportunidades.findByIdAndEmpresaId(p.oportunidadId(), p.empresaId())
                .orElseThrow(() -> new IllegalStateException("La oportunidad " + p.oportunidadId() + " no existe en MongoDB"));
        switch (p.tipo()) {
            case PendientesCrmService.CLIENTE_RESERVA -> {
                if (oportunidad.aplicarClienteDeReserva(p.clienteId())) {
                    oportunidades.guardar(oportunidad);
                }
            }
            case PendientesCrmService.VENTA_GANADA -> {
                LocalDateTime fecha = p.fecha() == null ? ahora() : p.fecha();
                if (oportunidad.aplicarVentaGanada(fecha, secuencias.siguiente(SecuenciasCrm.HISTORIAL_ETAPAS),
                        p.usuarioId(), p.contratoId())) {
                    oportunidades.guardar(oportunidad);
                }
                Prospecto prospecto = prospecto(oportunidad);
                if (prospecto.aplicarVentaGanada(p.clienteId())) {
                    prospectos.guardar(prospecto);
                }
            }
            case PendientesCrmService.VENTA_ANULADA -> {
                if (oportunidad.aplicarAnulacionVenta(secuencias.siguiente(SecuenciasCrm.HISTORIAL_ETAPAS),
                        p.usuarioId(), p.contratoId())) {
                    oportunidades.guardar(oportunidad);
                }
                prospectos.findByIdAndEmpresaId(oportunidad.getProspectoId(), oportunidad.getEmpresaId())
                        .ifPresent(prospecto -> {
                            if (prospecto.getEstado() == EstadoProspecto.CONVERTIDO) {
                                prospecto.reabrirPorAnulacionContrato();
                                prospectos.guardar(prospecto);
                            }
                        });
            }
            default -> throw new IllegalStateException("Tipo de pendiente desconocido: " + p.tipo());
        }
    }

    private Prospecto prospecto(Oportunidad oportunidad) {
        return prospectos.findByIdAndEmpresaId(oportunidad.getProspectoId(), oportunidad.getEmpresaId())
                .orElseThrow(() -> new IllegalStateException("El prospecto " + oportunidad.getProspectoId()
                        + " no existe en MongoDB"));
    }

    private List<Pendiente> leer(String condicion, Object... parametros) {
        Object[] conLimite = Arrays.copyOf(parametros, parametros.length + 1);
        conLimite[parametros.length] = LOTE;
        return jdbcTemplate.query("""
                SELECT id, empresa_id, oportunidad_id, tipo, cliente_id, contrato_id, usuario_id, fecha
                FROM crm_pendientes WHERE %s ORDER BY id LIMIT ?
                """.formatted(condicion), (rs, fila) -> new Pendiente(
                rs.getLong("id"), rs.getLong("empresa_id"), rs.getLong("oportunidad_id"), rs.getString("tipo"),
                rs.getObject("cliente_id", Long.class), rs.getObject("contrato_id", Long.class),
                rs.getObject("usuario_id", Long.class), rs.getObject("fecha", LocalDateTime.class)), conLimite);
    }

    /** Toma el pendiente por un rato; falso si otro proceso ya lo tiene o ya se aplicó. */
    private boolean tomar(long id) {
        LocalDateTime ahora = ahora();
        Integer filas = nuevaTransaccion.execute(estado -> jdbcTemplate.update("""
                UPDATE crm_pendientes SET tomado_hasta = ?
                WHERE id = ? AND procesado_en IS NULL AND (tomado_hasta IS NULL OR tomado_hasta < ?)
                """, Timestamp.valueOf(ahora.plus(Duration.ofSeconds(LEASE_SEGUNDOS))), id, Timestamp.valueOf(ahora)));
        return filas != null && filas == 1;
    }

    private void marcar(String cambios, long id, Object valor) {
        nuevaTransaccion.executeWithoutResult(estado ->
                jdbcTemplate.update("UPDATE crm_pendientes SET " + cambios + " WHERE id = ?", valor, id));
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= 700 ? texto : texto.substring(0, 700);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }

    private record Pendiente(long id, long empresaId, long oportunidadId, String tipo, Long clienteId,
                             Long contratoId, Long usuarioId, LocalDateTime fecha) { }
}
