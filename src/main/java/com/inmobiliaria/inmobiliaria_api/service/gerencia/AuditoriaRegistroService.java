package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.EventoAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registra eventos en la bitácora de auditoría (colección {@code auditoria_eventos} de MongoDB
 * desde la rama 7).
 *
 * <p>Se llama desde operaciones que corren en una transacción de MySQL. Como MongoDB no participa
 * en ella, el evento se escribe <em>después del commit</em>: igual que antes, una operación que
 * hace rollback no deja evento. Los datos y la fecha se toman en el momento de la llamada.</p>
 *
 * <p>Cada evento guardado pasa por {@link DetectorAnomaliasService}, que puede crear alertas.</p>
 *
 * <p>Si MongoDB falla al guardar, el evento completo queda en el log con nivel ERROR (para poder
 * recuperarlo) y la operación principal, que ya se confirmó, no se cae.</p>
 */
@Service
public class AuditoriaRegistroService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Logger log = LoggerFactory.getLogger(AuditoriaRegistroService.class);

    private final EventoAuditoriaRepository repository;
    private final SecuenciaService secuencias;
    private final ObjectMapper objectMapper;
    private final DetectorAnomaliasService detector;

    public AuditoriaRegistroService(EventoAuditoriaRepository repository,
                                    SecuenciaService secuencias,
                                    ObjectMapper objectMapper,
                                    DetectorAnomaliasService detector) {
        this.repository = repository;
        this.secuencias = secuencias;
        this.objectMapper = objectMapper;
        this.detector = detector;
    }

    public void registrar(ContextoUsuario contexto, Long proyectoId, String modulo,
                          String accion, String entidadTipo, Long entidadId,
                          String descripcion, Object anterior, Object nuevo) {
        Registro registro = new Registro(contexto.empresaId(), proyectoId, contexto.usuarioId(), modulo,
                accion, entidadTipo, entidadId, descripcion, json(anterior), json(nuevo),
                UUID.randomUUID().toString(), LocalDateTime.now(BOGOTA));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guardar(registro);
                }
            });
        } else {
            guardar(registro);
        }
    }

    private void guardar(Registro r) {
        EventoAuditoria evento;
        try {
            evento = repository.insertar(new EventoAuditoria(secuencias.siguiente(SecuenciasAuditoria.EVENTOS),
                    r.empresaId(), r.proyectoId(), r.actorId(), r.modulo(), r.accion(), r.entidadTipo(),
                    r.entidadId(), r.descripcion(), r.anterior(), r.nuevo(), null, null,
                    r.correlationId(), r.ocurridoEn()));
        } catch (RuntimeException e) {
            log.error("No se pudo guardar el evento de auditoría {}", r, e);
            return;
        }
        detector.evaluarEvento(evento);
    }

    private String json(Object valor) {
        if (valor == null) return null;
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"no_serializable\"}";
        }
    }

    /** Datos del evento tomados en el momento de la llamada. */
    private record Registro(Long empresaId, Long proyectoId, Long actorId, String modulo, String accion,
                            String entidadTipo, Long entidadId, String descripcion, String anterior,
                            String nuevo, String correlationId, LocalDateTime ocurridoEn) { }
}
