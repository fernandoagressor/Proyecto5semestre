package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.gerencia.AuditoriaDtos;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.ExportadorTablas;
import com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.Formato;
import com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.Tabla;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Descarga de la bitácora de auditoría con los mismos filtros y alcance que la consulta (hasta 5.000 eventos). */
@Service
public class AuditoriaExportacionService {

    private static final int PAGINAS = 50;

    private final AuditoriaConsultaService consulta;
    private final ExportadorTablas exportador;
    private final AuditoriaRegistroService auditoria;
    private final JdbcTemplate jdbc;

    public AuditoriaExportacionService(AuditoriaConsultaService consulta, ExportadorTablas exportador,
                                       AuditoriaRegistroService auditoria, JdbcTemplate jdbc) {
        this.consulta = consulta;
        this.exportador = exportador;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
    }

    public ReporteExportacionService.Archivo exportar(ContextoUsuario ctx, String formatoTexto, Long proyectoId,
                                                      Long actorId, String modulo, String accion,
                                                      LocalDateTime desde, LocalDateTime hasta) {
        Formato formato = Formato.de(formatoTexto);
        List<List<Object>> filas = new ArrayList<>();
        for (int pagina = 0; pagina < PAGINAS; pagina++) {
            PaginaResponse<AuditoriaDtos.EventoResponse> p = consulta.eventos(ctx, proyectoId, actorId, modulo, accion,
                    desde, hasta, pagina, 100);
            for (AuditoriaDtos.EventoResponse e : p.contenido()) {
                List<Object> fila = new ArrayList<>();
                fila.add(e.ocurridoEn());
                fila.add(e.modulo());
                fila.add(e.accion());
                fila.add(e.entidadTipo() == null ? null : e.entidadTipo() + (e.entidadId() == null ? "" : " #" + e.entidadId()));
                fila.add(e.descripcion());
                fila.add(e.actorId());
                fila.add(e.proyectoId());
                fila.add(e.ipOrigen());
                filas.add(fila);
            }
            if (p.ultima()) break;
        }
        Tabla tabla = new Tabla("Bitácora de auditoría", filtros(modulo, accion, desde, hasta),
                List.of("Fecha", "Módulo", "Acción", "Entidad", "Descripción", "Usuario", "Proyecto", "IP"), filas, null);
        String empresa = jdbc.queryForObject("SELECT nombre FROM empresas WHERE id = ?", String.class, ctx.empresaId());
        byte[] contenido = exportador.exportar(tabla, formato, empresa);
        auditoria.registrar(ctx, proyectoId, "AUDITORIA", "AUDITORIA_EXPORTADA", "AUDITORIA", null,
                "Bitácora exportada (" + formato + ", " + filas.size() + " eventos)", null, null);
        return new ReporteExportacionService.Archivo("auditoria." + formato.extension(), formato.mime(), contenido);
    }

    private static String filtros(String modulo, String accion, LocalDateTime desde, LocalDateTime hasta) {
        List<String> partes = new ArrayList<>();
        if (modulo != null && !modulo.isBlank()) partes.add("Módulo " + modulo);
        if (accion != null && !accion.isBlank()) partes.add("Acción " + accion);
        if (desde != null) partes.add("Desde " + desde.toLocalDate());
        if (hasta != null) partes.add("Hasta " + hasta.toLocalDate());
        return String.join(" · ", partes);
    }
}
