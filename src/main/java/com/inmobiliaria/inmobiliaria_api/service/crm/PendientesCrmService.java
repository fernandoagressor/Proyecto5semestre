package com.inmobiliaria.inmobiliaria_api.service.crm;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cola de cambios del CRM que provoca la venta (tabla {@code crm_pendientes} de MySQL).
 *
 * <p>La reserva, la firma y la anulación del contrato son transacciones de MySQL, pero la
 * oportunidad y el prospecto viven en MongoDB. Para no perder ningún cambio, la venta escribe el
 * pendiente en la misma transacción de MySQL (si la venta hace rollback, el pendiente tampoco
 * existe). Al confirmarse se aplica de inmediato en MongoDB; si MongoDB falla, el
 * {@link ProcesadorPendientesCrm} lo reintenta.</p>
 */
@Service
public class PendientesCrmService {

    static final String CLIENTE_RESERVA = "CLIENTE_RESERVA";
    static final String VENTA_GANADA = "VENTA_GANADA";
    static final String VENTA_ANULADA = "VENTA_ANULADA";

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final JdbcTemplate jdbcTemplate;
    private final ProcesadorPendientesCrm procesador;

    public PendientesCrmService(JdbcTemplate jdbcTemplate, ProcesadorPendientesCrm procesador) {
        this.jdbcTemplate = jdbcTemplate;
        this.procesador = procesador;
    }

    /** La reserva vincula el cliente a la oportunidad. */
    public void clienteDeReserva(Long empresaId, Long oportunidadId, Long clienteId, Long usuarioId) {
        registrar(CLIENTE_RESERVA, empresaId, oportunidadId, clienteId, null, usuarioId, null);
    }

    /** La firma completa del contrato gana la oportunidad y convierte el prospecto. */
    public void ventaGanada(Long empresaId, Long oportunidadId, Long contratoId, Long clienteId,
                            LocalDateTime fecha, Long usuarioId) {
        registrar(VENTA_GANADA, empresaId, oportunidadId, clienteId, contratoId, usuarioId, fecha);
    }

    /** La anulación del contrato reabre la oportunidad y el prospecto. */
    public void ventaAnulada(Long empresaId, Long oportunidadId, Long contratoId, Long usuarioId) {
        registrar(VENTA_ANULADA, empresaId, oportunidadId, null, contratoId, usuarioId, null);
    }

    private void registrar(String tipo, Long empresaId, Long oportunidadId, Long clienteId,
                           Long contratoId, Long usuarioId, LocalDateTime fecha) {
        if (oportunidadId == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO crm_pendientes
                (empresa_id, oportunidad_id, tipo, cliente_id, contrato_id, usuario_id, fecha, creado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, empresaId, oportunidadId, tipo, clienteId, contratoId, usuarioId, fecha,
                LocalDateTime.now(BOGOTA));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    procesador.procesarOportunidad(oportunidadId);
                }
            });
        } else {
            procesador.procesarOportunidad(oportunidadId);
        }
    }
}
