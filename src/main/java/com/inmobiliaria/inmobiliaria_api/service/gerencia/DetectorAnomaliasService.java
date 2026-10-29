package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.gerencia.AlertaAnomalia;
import com.inmobiliaria.inmobiliaria_api.entity.gerencia.EventoAuditoria;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.IntentoAcceso;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.AlertaAnomaliaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.gerencia.EventoAuditoriaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.IntentoAccesoRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Crea alertas de anomalía a partir de los intentos de acceso y de los eventos de auditoría.
 *
 * <table>
 *   <caption>Reglas</caption>
 *   <tr><th>Tipo</th><th>Cuándo</th><th>Severidad</th></tr>
 *   <tr><td>{@value #CUENTA_BLOQUEADA}</td><td>Una cuenta se bloquea por intentos fallidos.</td><td>ALTA</td></tr>
 *   <tr><td>{@value #IP_NUEVA}</td><td>Inicio de sesión exitoso desde una IP que el usuario no había
 *       usado (dentro de los 90 días de historial). No aplica al primer inicio de sesión.</td><td>BAJA</td></tr>
 *   <tr><td>{@value #EXPORTACIONES_REPETIDAS}</td><td>{@value #MAX_EXPORTACIONES} o más exportaciones de
 *       auditoría del mismo usuario en 24 horas.</td><td>ALTA</td></tr>
 *   <tr><td>{@value #ADMIN_FUERA_DE_HORARIO}</td><td>Acción de superadministración entre las 22:00 y las
 *       06:00 (hora de Bogotá).</td><td>MEDIA</td></tr>
 * </table>
 *
 * <p>No se crea una alerta si ya hay una abierta (o en investigación) del mismo tipo para el
 * mismo usuario en la empresa. Cada alerta nueva se notifica a los usuarios activos de la empresa
 * con el permiso {@code AUDITORIA_ALERTAS_GESTIONAR}.</p>
 *
 * <p>La detección nunca interrumpe la operación que la origina: cualquier error queda en el log.</p>
 */
@Service
public class DetectorAnomaliasService {

    static final String CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";
    static final String IP_NUEVA = "ACCESO_DESDE_IP_NUEVA";
    static final String EXPORTACIONES_REPETIDAS = "EXPORTACIONES_REPETIDAS";
    static final String ADMIN_FUERA_DE_HORARIO = "ACCION_ADMIN_FUERA_DE_HORARIO";
    static final int MAX_EXPORTACIONES = 3;
    static final int HORA_INICIO_JORNADA = 6;
    static final int HORA_FIN_JORNADA = 22;
    static final String PERMISO_GESTION = "AUDITORIA_ALERTAS_GESTIONAR";

    private static final Logger log = LoggerFactory.getLogger(DetectorAnomaliasService.class);

    private final AlertaAnomaliaRepository alertas;
    private final IntentoAccesoRepository intentos;
    private final EventoAuditoriaRepository eventos;
    private final SecuenciaService secuencias;
    private final NotificacionService notificaciones;
    private final JdbcTemplate jdbcTemplate;

    public DetectorAnomaliasService(AlertaAnomaliaRepository alertas,
                                    IntentoAccesoRepository intentos,
                                    EventoAuditoriaRepository eventos,
                                    SecuenciaService secuencias,
                                    NotificacionService notificaciones,
                                    JdbcTemplate jdbcTemplate) {
        this.alertas = alertas;
        this.intentos = intentos;
        this.eventos = eventos;
        this.secuencias = secuencias;
        this.notificaciones = notificaciones;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Evalúa un intento de acceso ya guardado. {@code empresaId} es la empresa del usuario, si se conoce. */
    public void evaluarIntento(IntentoAcceso intento, Long empresaId) {
        try {
            if (intento.getUsuarioId() == null || empresaId == null) {
                return;
            }
            if ("BLOQUEADO".equals(intento.getResultado())) {
                crear(empresaId, CUENTA_BLOQUEADA, "ALTA", intento.getUsuarioId(), null,
                        "La cuenta " + intento.getIdentificador() + " se bloqueó por intentos fallidos"
                                + desde(intento.getIpOrigen()));
            } else if ("EXITOSO".equals(intento.getResultado()) && intento.getIpOrigen() != null
                    && intentos.contarExitosos(intento.getUsuarioId(), intento.getId()) > 0
                    && !intentos.existeExitosoDesdeIp(intento.getUsuarioId(), intento.getIpOrigen(), intento.getId())) {
                crear(empresaId, IP_NUEVA, "BAJA", intento.getUsuarioId(), null,
                        "Inicio de sesión de " + intento.getIdentificador() + " desde una IP nueva: "
                                + intento.getIpOrigen());
            }
        } catch (RuntimeException e) {
            log.error("No se pudo evaluar el intento de acceso {} de {}", intento.getId(), intento.getIdentificador(), e);
        }
    }

    /** Evalúa un evento de auditoría ya guardado. */
    public void evaluarEvento(EventoAuditoria evento) {
        try {
            if (evento.getEmpresaId() == null || evento.getActorId() == null) {
                return;
            }
            if ("AUDITORIA_EXPORTAR_SOLICITAR".equals(evento.getAccion())
                    && eventos.contarAcciones(evento.getActorId(), evento.getAccion(),
                    evento.getOcurridoEn().minusHours(24)) >= MAX_EXPORTACIONES) {
                crear(evento.getEmpresaId(), EXPORTACIONES_REPETIDAS, "ALTA", evento.getActorId(), evento.getId(),
                        "El usuario pidió " + MAX_EXPORTACIONES + " o más exportaciones de auditoría en 24 horas");
            }
            if ("SUPERADMINISTRACION".equals(evento.getModulo()) && fueraDeHorario(evento.getOcurridoEn())) {
                crear(evento.getEmpresaId(), ADMIN_FUERA_DE_HORARIO, "MEDIA", evento.getActorId(), evento.getId(),
                        "Acción de superadministración fuera de horario: " + evento.getAccion()
                                + " (" + evento.getDescripcion() + ")");
            }
        } catch (RuntimeException e) {
            log.error("No se pudo evaluar el evento de auditoría {}", evento.getId(), e);
        }
    }

    static boolean fueraDeHorario(LocalDateTime fecha) {
        int hora = fecha.getHour();
        return hora >= HORA_FIN_JORNADA || hora < HORA_INICIO_JORNADA;
    }

    private void crear(Long empresaId, String tipo, String severidad, Long usuarioId,
                       Long eventoId, String descripcion) {
        if (alertas.existeAbierta(empresaId, tipo, usuarioId)) {
            return;
        }
        AlertaAnomalia alerta = alertas.insertar(new AlertaAnomalia(secuencias.siguiente(SecuenciasAuditoria.ALERTAS),
                empresaId, tipo, severidad, usuarioId, eventoId, descripcion));
        String prioridad = "BAJA".equals(severidad) ? "MEDIA" : "ALTA";
        for (Long destinatario : gestores(empresaId)) {
            notificaciones.crearInmediata(empresaId, destinatario, "ALERTA_ANOMALIA",
                    "Alerta de seguridad: " + tipo, descripcion, "ALERTA_ANOMALIA", alerta.getId(), prioridad);
        }
    }

    private List<Long> gestores(Long empresaId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT u.id
                FROM usuarios u
                JOIN usuario_roles ur ON ur.usuario_id = u.id
                JOIN roles r ON r.id = ur.rol_id AND r.activo = 1
                JOIN rol_permisos rp ON rp.rol_id = r.id AND rp.concedido = 1
                JOIN permisos p ON p.id = rp.permiso_id AND p.activo = 1
                WHERE u.empresa_id = ? AND u.estado = 'ACTIVO' AND p.codigo = ?
                """, Long.class, empresaId, PERMISO_GESTION);
    }

    private static String desde(String ip) {
        return ip == null ? "" : " (IP " + ip + ")";
    }
}
