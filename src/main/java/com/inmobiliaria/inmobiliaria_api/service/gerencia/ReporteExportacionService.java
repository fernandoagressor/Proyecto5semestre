package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.ExportadorTablas;
import com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.Formato;
import com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.Tabla;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reportes descargables (vista "Reportes" de Gerencia) en CSV, XLSX o PDF. Respetan el alcance:
 * quien solo tiene proyectos asignados exporta únicamente esos proyectos.
 */
@Service
public class ReporteExportacionService {

    public enum Tipo { VENTAS, RECAUDO, CARTERA, INVENTARIO, COMISIONES }

    public record Archivo(String nombre, String mime, byte[] contenido) { }

    private static final int MAX_FILAS = 20_000;

    private final JdbcTemplate jdbc;
    private final AlcanceProyectoService alcance;
    private final ExportadorTablas exportador;
    private final AuditoriaRegistroService auditoria;

    public ReporteExportacionService(JdbcTemplate jdbc, AlcanceProyectoService alcance, ExportadorTablas exportador,
                                     AuditoriaRegistroService auditoria) {
        this.jdbc = jdbc;
        this.alcance = alcance;
        this.exportador = exportador;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Archivo exportar(ContextoUsuario ctx, String tipoTexto, String formatoTexto, LocalDate desde,
                            LocalDate hasta, Long proyectoId) {
        Tipo tipo = tipo(tipoTexto);
        Formato formato = Formato.de(formatoTexto);
        LocalDate fin = hasta == null ? LocalDate.now() : hasta;
        LocalDate inicio = desde == null ? fin.withDayOfMonth(1) : desde;
        if (inicio.isAfter(fin)) {
            throw new ReglaNegocioException("La fecha inicial no puede ser posterior a la final");
        }
        if (inicio.plusYears(5).isBefore(fin)) {
            throw new ReglaNegocioException("El periodo máximo es de 5 años");
        }
        String filtroProyecto = filtroProyecto(ctx, proyectoId);
        String periodo = "Del " + inicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " al " + fin.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        Tabla tabla = switch (tipo) {
            case VENTAS -> ventas(ctx, filtroProyecto, inicio, fin, periodo);
            case RECAUDO -> recaudo(ctx, filtroProyecto, inicio, fin, periodo);
            case CARTERA -> cartera(ctx, filtroProyecto, fin);
            case INVENTARIO -> inventario(ctx, filtroProyecto);
            case COMISIONES -> comisiones(ctx, filtroProyecto, inicio, fin, periodo);
        };
        String empresa = jdbc.queryForObject("SELECT nombre FROM empresas WHERE id = ?", String.class, ctx.empresaId());
        byte[] contenido = exportador.exportar(tabla, formato, empresa);
        auditoria.registrar(ctx, proyectoId, "REPORTES", "REPORTE_EXPORTADO", "REPORTE", null,
                tabla.titulo() + " (" + formato + ", " + tabla.filas().size() + " filas)", null, periodo);
        String nombre = tipo.name().toLowerCase(Locale.ROOT) + "-" + fin + "." + formato.extension();
        return new Archivo(nombre, formato.mime(), contenido);
    }

    private Tabla ventas(ContextoUsuario ctx, String filtro, LocalDate desde, LocalDate hasta, String periodo) {
        List<List<Object>> filas = jdbc.query("""
                SELECT c.codigo, c.fecha_firma, p.nombre proyecto, l.codigo lote, CONCAT_WS(' ', pe.nombres, pe.apellidos) cliente,
                       u.nombre_usuario asesor, c.valor_total, c.cuota_inicial, c.numero_cuotas, c.estado
                FROM contratos c
                JOIN proyectos p ON p.id = c.proyecto_id
                JOIN lotes l ON l.id = c.lote_id
                JOIN clientes cl ON cl.id = c.cliente_principal_id
                JOIN personas pe ON pe.id = cl.persona_id
                LEFT JOIN usuarios u ON u.id = c.asesor_id
                WHERE c.empresa_id = ? AND c.estado IN ('VIGENTE','FINALIZADO','PENDIENTE_FIRMA')
                  AND COALESCE(c.fecha_firma, DATE(c.creado_en)) BETWEEN ? AND ?
                """ + filtro.replace("{p}", "c.proyecto_id") + " ORDER BY c.fecha_firma, c.codigo LIMIT " + MAX_FILAS,
                (rs, n) -> fila(rs.getString(1), fecha(rs, 2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getInt(9), rs.getString(10)),
                ctx.empresaId(), Date.valueOf(desde), Date.valueOf(hasta));
        return new Tabla("Ventas del periodo", periodo,
                List.of("Contrato", "Firma", "Proyecto", "Lote", "Cliente", "Asesor", "Valor", "Cuota inicial", "Cuotas", "Estado"),
                filas, List.of(false, false, false, false, false, false, true, true, false, false));
    }

    private Tabla recaudo(ContextoUsuario ctx, String filtro, LocalDate desde, LocalDate hasta, String periodo) {
        List<List<Object>> filas = jdbc.query("""
                SELECT pg.codigo, pg.fecha_pago, CONCAT_WS(' ', pe.nombres, pe.apellidos) cliente, c.codigo contrato,
                       p.nombre proyecto, mp.nombre metodo, pg.referencia, pg.valor
                FROM pagos pg
                JOIN clientes cl ON cl.id = pg.cliente_id
                JOIN personas pe ON pe.id = cl.persona_id
                JOIN metodos_pago mp ON mp.id = pg.metodo_pago_id
                LEFT JOIN contratos c ON c.id = pg.contrato_id
                LEFT JOIN proyectos p ON p.id = c.proyecto_id
                WHERE pg.empresa_id = ? AND pg.estado = 'CONFIRMADO' AND DATE(pg.fecha_pago) BETWEEN ? AND ?
                """ + filtro.replace("{p}", "c.proyecto_id") + " ORDER BY pg.fecha_pago LIMIT " + MAX_FILAS,
                (rs, n) -> fila(rs.getString(1), fechaHora(rs, 2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getBigDecimal(8)),
                ctx.empresaId(), Date.valueOf(desde), Date.valueOf(hasta));
        return new Tabla("Recaudo confirmado", periodo,
                List.of("Pago", "Fecha", "Cliente", "Contrato", "Proyecto", "Método", "Referencia", "Valor"),
                filas, List.of(false, false, false, false, false, false, false, true));
    }

    private Tabla cartera(ContextoUsuario ctx, String filtro, LocalDate corte) {
        List<List<Object>> filas = jdbc.query("""
                SELECT c.codigo, CONCAT_WS(' ', pe.nombres, pe.apellidos) cliente, pe.telefono, p.nombre proyecto,
                       q.numero, q.fecha_vencimiento, GREATEST(DATEDIFF(?, q.fecha_vencimiento), 0) dias,
                       q.saldo, q.interes_mora, q.saldo + q.interes_mora total
                FROM cuotas q
                JOIN planes_pago pp ON pp.id = q.plan_pago_id AND pp.estado = 'ACTIVO'
                JOIN contratos c ON c.id = q.contrato_id
                JOIN proyectos p ON p.id = c.proyecto_id
                JOIN clientes cl ON cl.id = c.cliente_principal_id
                JOIN personas pe ON pe.id = cl.persona_id
                WHERE q.empresa_id = ? AND q.estado IN ('PENDIENTE','PARCIAL','VENCIDA') AND q.fecha_vencimiento <= ?
                """ + filtro.replace("{p}", "c.proyecto_id") + " ORDER BY dias DESC, c.codigo, q.numero LIMIT " + MAX_FILAS,
                (rs, n) -> fila(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        fecha(rs, 6), rs.getInt(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10)),
                Date.valueOf(corte), ctx.empresaId(), Date.valueOf(corte));
        return new Tabla("Cartera vencida y por vencer", "Corte al " + corte.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                List.of("Contrato", "Cliente", "Teléfono", "Proyecto", "Cuota", "Vence", "Días de mora", "Saldo", "Interés de mora", "Total"),
                filas, List.of(false, false, false, false, false, false, false, true, true, true));
    }

    private Tabla inventario(ContextoUsuario ctx, String filtro) {
        List<List<Object>> filas = jdbc.query("""
                SELECT p.nombre proyecto, e.nombre etapa, m.nombre manzana, l.codigo, l.numero, l.area_m2, l.valor_lista, l.estado
                FROM lotes l
                JOIN proyectos p ON p.id = l.proyecto_id
                JOIN etapas_proyecto e ON e.id = l.etapa_id
                JOIN manzanas m ON m.id = l.manzana_id
                WHERE l.empresa_id = ?
                """ + filtro.replace("{p}", "l.proyecto_id") + " ORDER BY p.nombre, e.secuencia, m.secuencia, l.codigo LIMIT " + MAX_FILAS,
                (rs, n) -> fila(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getString(8)),
                ctx.empresaId());
        return new Tabla("Inventario de lotes", null,
                List.of("Proyecto", "Etapa", "Manzana", "Código", "Número", "Área m²", "Valor de lista", "Estado"),
                filas, List.of(false, false, false, false, false, false, true, false));
    }

    private Tabla comisiones(ContextoUsuario ctx, String filtro, LocalDate desde, LocalDate hasta, String periodo) {
        List<List<Object>> filas = jdbc.query("""
                SELECT u.nombre_usuario asesor, c.codigo contrato, p.nombre proyecto, co.base_calculo, co.porcentaje,
                       co.valor_comision, co.estado, co.fecha_causacion, co.fecha_pago
                FROM comisiones co
                JOIN proyectos p ON p.id = co.proyecto_id
                LEFT JOIN contratos c ON c.id = co.contrato_id
                LEFT JOIN usuarios u ON u.id = co.asesor_id
                WHERE co.empresa_id = ? AND DATE(co.creado_en) BETWEEN ? AND ?
                """ + filtro.replace("{p}", "co.proyecto_id") + " ORDER BY u.nombre_usuario, co.creado_en LIMIT " + MAX_FILAS,
                (rs, n) -> fila(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                        rs.getBigDecimal(6), rs.getString(7), fecha(rs, 8), fecha(rs, 9)),
                ctx.empresaId(), Date.valueOf(desde), Date.valueOf(hasta));
        return new Tabla("Comisiones", periodo,
                List.of("Asesor", "Contrato", "Proyecto", "Base", "%", "Comisión", "Estado", "Causada", "Pagada"),
                filas, List.of(false, false, false, true, false, true, false, false, false));
    }

    /** Fragmento SQL con el alcance de proyectos ({p} se reemplaza por la columna). Solo ids numéricos. */
    private String filtroProyecto(ContextoUsuario ctx, Long proyectoId) {
        AlcanceProyectos a = alcance.obtener(ctx.usuarioId(), ctx.empresaId());
        if (proyectoId != null) {
            if (!a.empresaCompleta() && !a.proyectos().contains(proyectoId)) {
                throw new AccesoDenegadoException("No tiene acceso a ese proyecto");
            }
            return " AND {p} = " + proyectoId;
        }
        if (a.empresaCompleta()) return "";
        if (a.proyectos().isEmpty()) {
            throw new AccesoDenegadoException("Exportar reportes requiere alcance sobre la empresa o sobre proyectos");
        }
        return " AND {p} IN (" + a.proyectos().stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
    }

    private static Tipo tipo(String valor) {
        try {
            return Tipo.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ReglaNegocioException("Tipo de reporte no válido: use " + Arrays.toString(Tipo.values()));
        }
    }

    private static List<Object> fila(Object... valores) {
        return new ArrayList<>(Arrays.asList(valores));
    }

    private static LocalDate fecha(ResultSet rs, int i) throws SQLException {
        Date d = rs.getDate(i);
        return d == null ? null : d.toLocalDate();
    }

    private static Object fechaHora(ResultSet rs, int i) throws SQLException {
        Timestamp t = rs.getTimestamp(i);
        return t == null ? null : t.toLocalDateTime();
    }
}
