package com.inmobiliaria.inmobiliaria_api.service.notificacion;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.notificacion.NotificacionDtos;
import com.inmobiliaria.inmobiliaria_api.entity.notificacion.Notificacion;
import com.inmobiliaria.inmobiliaria_api.repository.notificacion.NotificacionRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Notificaciones dentro de la aplicación. Desde la rama 6 viven en MongoDB.
 *
 * <p>Quien notifica suele estar dentro de una transacción de MySQL (aprobar un cambio de datos,
 * programar una cita…). Como MongoDB no participa en esa transacción, la notificación se escribe
 * <em>después del commit</em>: si la operación hace rollback, no queda una notificación de algo
 * que no pasó. Un fallo al escribir la notificación se registra en el log y no se propaga, porque
 * la operación principal ya quedó confirmada.</p>
 */
@Service
public class NotificacionService {
    static final String SECUENCIA = "notificaciones";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository repository;
    private final SecuenciaService secuencias;

    public NotificacionService(NotificacionRepository repository, SecuenciaService secuencias) {
        this.repository = repository;
        this.secuencias = secuencias;
    }

    /**
     * Crea una notificación para el usuario. Sin usuario no hace nada. Dentro de una transacción
     * se escribe al confirmarla; fuera de ella, de inmediato.
     */
    public void crear(Long empresaId, Long usuarioId, String tipo, String titulo,
                      String mensaje, String entidadTipo, Long entidadId, String prioridad) {
        if (usuarioId == null) {
            return;
        }
        Notificacion notificacion = new Notificacion(empresaId, usuarioId, tipo, titulo, mensaje,
                entidadTipo, entidadId, prioridad, null);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guardar(notificacion);
                }
            });
        } else {
            guardar(notificacion);
        }
    }

    /**
     * Crea la notificación de inmediato, sin esperar a ninguna transacción. Es para código que ya
     * corre después de un commit (por ejemplo, la detección de anomalías): ahí una sincronización
     * nueva ya no se ejecutaría.
     */
    public void crearInmediata(Long empresaId, Long usuarioId, String tipo, String titulo,
                               String mensaje, String entidadTipo, Long entidadId, String prioridad) {
        if (usuarioId == null) {
            return;
        }
        guardar(new Notificacion(empresaId, usuarioId, tipo, titulo, mensaje, entidadTipo, entidadId,
                prioridad, null));
    }

    public PaginaResponse<NotificacionDtos.Response> listar(ContextoUsuario contexto,
                                                            Boolean leida, int pagina, int tamano) {
        return PaginaResponse.from(repository.buscarVigentes(contexto.usuarioId(), leida, ahora(),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")))),
                NotificacionDtos.Response::from);
    }

    public NotificacionDtos.Response marcarLeida(ContextoUsuario contexto, Long id) {
        return repository.marcarLeida(id, contexto.usuarioId(), ahora())
                .map(NotificacionDtos.Response::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("La notificación no existe"));
    }

    public NotificacionDtos.Resumen resumen(ContextoUsuario contexto) {
        return new NotificacionDtos.Resumen(repository.contarVigentesNoLeidas(contexto.usuarioId(), ahora()));
    }

    private void guardar(Notificacion notificacion) {
        try {
            notificacion.identificar(secuencias.siguiente(SECUENCIA));
            repository.insertar(notificacion);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar la notificación {} para el usuario {}",
                    notificacion.getTipo(), notificacion.getUsuarioId(), e);
        }
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
