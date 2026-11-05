package com.inmobiliaria.inmobiliaria_api.service.inventario;

import com.inmobiliaria.inmobiliaria_api.entity.inventario.Proyecto;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import com.inmobiliaria.inmobiliaria_api.service.notificacion.NotificacionService;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Deja rastro de cada cambio del inventario (etapas, manzanas y lotes): un evento de auditoría y
 * una notificación para quienes administran el inventario o revisan la auditoría de la empresa
 * (permisos {@code ETAPAS_GESTIONAR} o {@code AUDITORIA_VER}), incluido quien hizo el cambio.
 * Ambos se escriben al confirmar la transacción: si el cambio se deshace, no queda aviso.
 */
@Service
public class InventarioAvisos {

    static final String MODULO = "INVENTARIO";
    static final String TIPO_NOTIFICACION = "INVENTARIO";

    private final AuditoriaRegistroService auditoria;
    private final NotificacionService notificaciones;
    private final JdbcTemplate jdbc;

    public InventarioAvisos(
            AuditoriaRegistroService auditoria,
            NotificacionService notificaciones,
            JdbcTemplate jdbc
    ) {
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.jdbc = jdbc;
    }

    /**
     * @param accion       código corto para la auditoría (ETAPA_CREADA, LOTE_EDITADO…)
     * @param titulo       título de la notificación ("Lote E01-M02-L05 actualizado")
     * @param queHizo      lo que hizo el usuario, en minúscula ("actualizó el lote E01-M02-L05")
     * @param anterior     datos antes del cambio (o {@code null} al crear)
     * @param nuevo        datos después del cambio
     */
    public void registrar(
            ContextoUsuario contexto,
            Proyecto proyecto,
            String accion,
            String entidadTipo,
            Long entidadId,
            String titulo,
            String queHizo,
            Object anterior,
            Object nuevo
    ) {
        String descripcion = nombreUsuario(contexto.usuarioId()) + " " + queHizo + " en " + proyecto.getNombre() + ".";
        auditoria.registrar(contexto, proyecto.getId(), MODULO, accion, entidadTipo, entidadId,
                descripcion, anterior, nuevo);
        for (Long destinatario : destinatarios(contexto.empresaId())) {
            notificaciones.crear(contexto.empresaId(), destinatario, TIPO_NOTIFICACION, titulo, descripcion,
                    "PROYECTO", proyecto.getId(), "MEDIA");
        }
    }

    private List<Long> destinatarios(Long empresaId) {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id
                FROM usuarios u
                JOIN usuario_roles ur ON ur.usuario_id = u.id
                JOIN roles r ON r.id = ur.rol_id AND r.activo = 1
                JOIN rol_permisos rp ON rp.rol_id = r.id AND rp.concedido = 1
                JOIN permisos p ON p.id = rp.permiso_id AND p.activo = 1
                WHERE u.empresa_id = ? AND u.estado = 'ACTIVO' AND u.tipo_usuario = 'INTERNO'
                  AND p.codigo IN ('ETAPAS_GESTIONAR', 'AUDITORIA_VER')
                """, Long.class, empresaId);
    }

    private String nombreUsuario(Long usuarioId) {
        List<String> nombres = jdbc.queryForList(
                "SELECT nombre_usuario FROM usuarios WHERE id = ?", String.class, usuarioId);
        return nombres.isEmpty() ? "Un usuario" : nombres.get(0);
    }
}
