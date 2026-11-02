package com.inmobiliaria.inmobiliaria_api.service.ventas.desistimiento;

import static com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.ExportadorTablas.encabezado;
import static com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.ExportadorTablas.pesos;

import com.inmobiliaria.inmobiliaria_api.dto.ventas.desistimiento.DesistimientoDtos;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import com.inmobiliaria.inmobiliaria_api.service.ventas.contrato.ContratoService;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desistimiento de una compra: liquidación (pagado − cláusula penal − otros descuentos), solicitud,
 * aprobación con separación de funciones y acta en PDF. Al aprobar se anula el contrato (lo que
 * libera el lote y reabre la oportunidad), se cancelan el plan y las cuotas abiertas y se anulan
 * las comisiones que no se han pagado.
 */
@Service
public class DesistimientoService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter CODIGO = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final List<String> CONTRATOS_DESISTIBLES = List.of("VIGENTE", "PENDIENTE_FIRMA", "NEGOCIACION");

    private final JdbcTemplate jdbc;
    private final ContratoService contratos;
    private final UsuarioRepository usuarios;
    private final AuditoriaRegistroService auditoria;

    public DesistimientoService(JdbcTemplate jdbc, ContratoService contratos, UsuarioRepository usuarios,
                                AuditoriaRegistroService auditoria) {
        this.jdbc = jdbc;
        this.contratos = contratos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public List<DesistimientoDtos.Response> listar(ContextoUsuario ctx, String estado) {
        String filtro = estado == null || estado.isBlank() ? "" : " AND d.estado = ?";
        Object[] args = filtro.isEmpty() ? new Object[] {ctx.empresaId()} : new Object[] {ctx.empresaId(), estado.toUpperCase(Locale.ROOT)};
        return jdbc.query(SELECT + " WHERE d.empresa_id = ?" + filtro + " ORDER BY d.creado_en DESC LIMIT 200",
                (rs, n) -> respuesta(rs), args).stream()
                .filter(d -> permitido(ctx, d.contratoId())).toList();
    }

    @Transactional(readOnly = true)
    public DesistimientoDtos.Response obtener(ContextoUsuario ctx, Long id) {
        DesistimientoDtos.Response d = buscar(ctx, id);
        contratos.obtenerAutorizado(ctx, d.contratoId());
        return d;
    }

    @Transactional(readOnly = true)
    public DesistimientoDtos.Liquidacion liquidar(ContextoUsuario ctx, Long contratoId, BigDecimal porcentaje,
                                                  BigDecimal otros) {
        contratos.obtenerAutorizado(ctx, contratoId);
        return liquidacion(ctx.empresaId(), contratoId, porcentaje, otros);
    }

    // ---------- Flujo ----------

    @Transactional
    public DesistimientoDtos.Response solicitar(ContextoUsuario ctx, DesistimientoDtos.SolicitudRequest request) {
        contratos.obtenerAutorizado(ctx, request.contratoId());
        DesistimientoDtos.Liquidacion l = liquidacion(ctx.empresaId(), request.contratoId(),
                request.porcentajePenalidad(), request.otrosDescuentos());
        if (!CONTRATOS_DESISTIBLES.contains(l.estadoContrato())) {
            throw new ReglaNegocioException("Solo se registra desistimiento de contratos en negociación, por firmar o vigentes");
        }
        Long[] ids = jdbc.queryForObject("SELECT proyecto_id, cliente_principal_id, lote_id FROM contratos WHERE id = ?",
                (rs, n) -> new Long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, request.contratoId());
        String codigo = "DES-" + LocalDateTime.now(BOGOTA).format(CODIGO) + "-" + request.contratoId();
        try {
            jdbc.update("""
                    INSERT INTO desistimientos (empresa_id, proyecto_id, contrato_id, cliente_id, lote_id, codigo, origen,
                        motivo, total_pagado, porcentaje_penalidad, valor_penalidad, otros_descuentos, valor_devolver,
                        fecha_devolucion, observaciones, solicitado_por)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, ctx.empresaId(), ids[0], request.contratoId(), ids[1], ids[2], codigo, request.origen(),
                    request.motivo().trim(), l.totalPagado(), l.porcentajePenalidad(), l.valorPenalidad(),
                    l.otrosDescuentos(), l.valorDevolver(),
                    request.fechaDevolucion() == null ? null : Date.valueOf(request.fechaDevolucion()),
                    opcional(request.observaciones()), ctx.usuarioId());
        } catch (DuplicateKeyException e) {
            throw new ConflictoException("El contrato ya tiene un desistimiento pendiente de aprobación");
        }
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        DesistimientoDtos.Response d = buscar(ctx, id);
        auditoria.registrar(ctx, ids[0], "VENTAS", "DESISTIMIENTO_SOLICITADO", "CONTRATO", request.contratoId(),
                "Desistimiento " + codigo + ": devolver " + pesos(l.valorDevolver()), null, d);
        return d;
    }

    @Transactional
    public DesistimientoDtos.Response aprobar(ContextoUsuario ctx, Long id, DesistimientoDtos.RevisionRequest request) {
        DesistimientoDtos.Response d = pendiente(ctx, id, request.version());
        boolean superAdmin = usuarios.findRoleCodes(ctx.usuarioId()).contains("SUPER_ADMIN");
        if (!superAdmin && ctx.usuarioId().equals(d.solicitadoPor())) {
            throw new AccesoDenegadoException("Quien registra el desistimiento no puede aprobarlo");
        }
        String motivo = ("Desistimiento " + d.codigo() + ": " + d.motivo());
        contratos.anular(ctx, d.contratoId(), motivo.length() > 400 ? motivo.substring(0, 399) + "…" : motivo);
        jdbc.update("UPDATE planes_pago SET estado = 'CANCELADO' WHERE contrato_id = ? AND estado IN ('ACTIVO','BORRADOR')",
                d.contratoId());
        int cuotas = jdbc.update("""
                UPDATE cuotas SET estado = 'CANCELADA'
                WHERE contrato_id = ? AND empresa_id = ? AND estado IN ('PENDIENTE','PARCIAL','VENCIDA')
                """, d.contratoId(), ctx.empresaId());
        int comisiones = jdbc.update("""
                UPDATE comisiones SET estado = 'ANULADA'
                WHERE contrato_id = ? AND empresa_id = ? AND estado IN ('PROYECTADA','CAUSADA','APROBADA')
                """, d.contratoId(), ctx.empresaId());
        cerrar(ctx, d, "APROBADO", request.comentario(), cuotas, comisiones);
        DesistimientoDtos.Response r = buscar(ctx, id);
        auditoria.registrar(ctx, null, "VENTAS", "DESISTIMIENTO_APROBADO", "CONTRATO", d.contratoId(),
                "Desistimiento " + d.codigo() + " aprobado: " + cuotas + " cuotas canceladas, " + comisiones
                        + " comisiones anuladas, lote liberado", d, r);
        return r;
    }

    @Transactional
    public DesistimientoDtos.Response rechazar(ContextoUsuario ctx, Long id, DesistimientoDtos.RevisionRequest request) {
        DesistimientoDtos.Response d = pendiente(ctx, id, request.version());
        if (request.comentario() == null || request.comentario().isBlank()) {
            throw new ReglaNegocioException("Indique por qué se rechaza el desistimiento");
        }
        cerrar(ctx, d, "RECHAZADO", request.comentario(), 0, 0);
        DesistimientoDtos.Response r = buscar(ctx, id);
        auditoria.registrar(ctx, null, "VENTAS", "DESISTIMIENTO_RECHAZADO", "CONTRATO", d.contratoId(),
                "Desistimiento " + d.codigo() + " rechazado", d, r);
        return r;
    }

    // ---------- Acta ----------

    @Transactional(readOnly = true)
    public byte[] acta(ContextoUsuario ctx, Long id) {
        DesistimientoDtos.Response d = obtener(ctx, id);
        String[] empresa = jdbc.queryForObject("SELECT nombre, nit FROM empresas WHERE id = ?",
                (rs, n) -> new String[] {rs.getString(1), rs.getString(2)}, ctx.empresaId());
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 54, 54, 48, 48);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();
            String titulo = "APROBADO".equals(d.estado()) ? "Acta de desistimiento y liquidación"
                    : "Liquidación de desistimiento (" + d.estado().toLowerCase(Locale.ROOT) + ")";
            encabezado(doc, empresa[0], titulo, d.codigo());
            Font texto = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Color.BLACK);
            Paragraph p = new Paragraph("Entre " + empresa[0] + (empresa[1] == null ? "" : ", NIT " + empresa[1])
                    + ", y " + d.cliente() + " se deja constancia del desistimiento del contrato " + d.contrato()
                    + " sobre el lote " + d.lote() + " del proyecto " + d.proyecto() + ", por iniciativa "
                    + ("CLIENTE".equals(d.origen()) ? "del cliente" : "de la empresa") + ". Motivo: " + d.motivo() + ".", texto);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            p.setLeading(16);
            p.setSpacingAfter(12);
            doc.add(p);
            PdfPTable t = new PdfPTable(new float[] {3, 2});
            t.setWidthPercentage(80);
            t.setHorizontalAlignment(Element.ALIGN_LEFT);
            fila(t, "Total pagado por el cliente", pesos(d.totalPagado()), false);
            fila(t, "Cláusula penal (" + d.porcentajePenalidad().stripTrailingZeros().toPlainString() + " %)",
                    "− " + pesos(d.valorPenalidad()), false);
            fila(t, "Otros descuentos", "− " + pesos(d.otrosDescuentos()), false);
            fila(t, "Valor a devolver", pesos(d.valorDevolver()), true);
            doc.add(t);
            Paragraph detalle = new Paragraph((d.fechaDevolucion() == null ? "" : "La devolución se hará a más tardar el "
                    + d.fechaDevolucion().format(FECHA) + ". ")
                    + ("APROBADO".equals(d.estado())
                    ? "Con la aprobación, el contrato quedó anulado, se cancelaron " + d.cuotasCanceladas()
                    + " cuotas pendientes, se anularon " + d.comisionesAnuladas()
                    + " comisiones no pagadas y el lote quedó disponible para la venta."
                    : "Este documento es una liquidación y no produce efectos hasta su aprobación.")
                    + (d.observaciones() == null ? "" : " Observaciones: " + d.observaciones()), texto);
            detalle.setSpacingBefore(12);
            detalle.setLeading(16);
            doc.add(detalle);
            if ("APROBADO".equals(d.estado())) {
                Paragraph firmas = new Paragraph("\n\n\n______________________________          ______________________________\n"
                        + empresa[0] + "                                        " + d.cliente(), texto);
                firmas.setSpacingBefore(28);
                doc.add(firmas);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el acta", e);
        } finally {
            doc.close();
        }
        auditoria.registrar(ctx, null, "VENTAS", "ACTA_DESISTIMIENTO_GENERADA", "CONTRATO", d.contratoId(),
                "Acta del desistimiento " + d.codigo(), null, null);
        return salida.toByteArray();
    }

    // ---------- Internos ----------

    private DesistimientoDtos.Liquidacion liquidacion(Long empresaId, Long contratoId, BigDecimal porcentaje, BigDecimal otros) {
        BigDecimal pct = porcentaje == null ? BigDecimal.TEN : porcentaje;
        if (pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ReglaNegocioException("La cláusula penal debe estar entre 0 y 100 %");
        }
        BigDecimal descuentos = otros == null ? BigDecimal.ZERO : otros.max(BigDecimal.ZERO);
        return jdbc.queryForObject("""
                SELECT c.id, c.codigo, c.estado, CONCAT_WS(' ', pe.nombres, pe.apellidos) cliente, p.nombre proyecto,
                       l.codigo lote, c.valor_total,
                       (SELECT COALESCE(SUM(pg.valor), 0) FROM pagos pg
                         WHERE pg.empresa_id = c.empresa_id AND pg.estado = 'CONFIRMADO'
                           AND (pg.contrato_id = c.id OR (pg.contrato_id IS NULL AND c.reserva_id IS NOT NULL AND pg.reserva_id = c.reserva_id))) pagado,
                       (SELECT COUNT(*) FROM cuotas q WHERE q.contrato_id = c.id AND q.estado IN ('PENDIENTE','PARCIAL','VENCIDA')) cuotas,
                       (SELECT COUNT(*) FROM comisiones co WHERE co.contrato_id = c.id AND co.estado IN ('PROYECTADA','CAUSADA','APROBADA')) com_pend,
                       (SELECT COUNT(*) FROM comisiones co WHERE co.contrato_id = c.id AND co.estado = 'PAGADA') com_pag
                FROM contratos c
                JOIN proyectos p ON p.id = c.proyecto_id
                JOIN lotes l ON l.id = c.lote_id
                JOIN clientes cl ON cl.id = c.cliente_principal_id
                JOIN personas pe ON pe.id = cl.persona_id
                WHERE c.id = ? AND c.empresa_id = ?
                """, (rs, n) -> {
                    BigDecimal pagado = rs.getBigDecimal("pagado");
                    BigDecimal penalidad = pagado.multiply(pct).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                    BigDecimal devolver = pagado.subtract(penalidad).subtract(descuentos).max(BigDecimal.ZERO);
                    return new DesistimientoDtos.Liquidacion(rs.getLong("id"), rs.getString("codigo"), rs.getString("estado"),
                            rs.getString("cliente"), rs.getString("proyecto"), rs.getString("lote"),
                            rs.getBigDecimal("valor_total"), pagado, pct, penalidad, descuentos, devolver,
                            rs.getLong("cuotas"), rs.getLong("com_pend"), rs.getLong("com_pag"));
                }, contratoId, empresaId);
    }

    private DesistimientoDtos.Response pendiente(ContextoUsuario ctx, Long id, Long version) {
        DesistimientoDtos.Response d = obtener(ctx, id);
        if (!"SOLICITADO".equals(d.estado())) {
            throw new ReglaNegocioException("El desistimiento ya fue " + d.estado().toLowerCase(Locale.ROOT));
        }
        if (version != null && version != d.version()) {
            throw new ConflictoException("El desistimiento cambió mientras lo revisaba; recargue e intente de nuevo");
        }
        return d;
    }

    private void cerrar(ContextoUsuario ctx, DesistimientoDtos.Response d, String estado, String comentario,
                        int cuotas, int comisiones) {
        int n = jdbc.update("""
                UPDATE desistimientos SET estado = ?, revisado_por = ?, revisado_en = CURRENT_TIMESTAMP(6),
                    comentario_revision = ?, cuotas_canceladas = ?, comisiones_anuladas = ?, version = version + 1
                WHERE id = ? AND empresa_id = ? AND estado = 'SOLICITADO' AND version = ?
                """, estado, ctx.usuarioId(), opcional(comentario), cuotas, comisiones, d.id(), ctx.empresaId(), d.version());
        if (n != 1) {
            throw new ConflictoException("El desistimiento cambió mientras lo revisaba; recargue e intente de nuevo");
        }
    }

    private DesistimientoDtos.Response buscar(ContextoUsuario ctx, Long id) {
        List<DesistimientoDtos.Response> lista = jdbc.query(SELECT + " WHERE d.id = ? AND d.empresa_id = ?",
                (rs, n) -> respuesta(rs), id, ctx.empresaId());
        if (lista.isEmpty()) {
            throw new RecursoNoEncontradoException("El desistimiento no existe");
        }
        return lista.get(0);
    }

    private boolean permitido(ContextoUsuario ctx, Long contratoId) {
        try {
            contratos.obtenerAutorizado(ctx, contratoId);
            return true;
        } catch (AccesoDenegadoException e) {
            return false;
        }
    }

    private static void fila(PdfPTable t, String etiqueta, String valor, boolean total) {
        Font f = FontFactory.getFont(total ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, total ? 11 : 10, Color.BLACK);
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, f));
        PdfPCell b = new PdfPCell(new Phrase(valor, f));
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell c : new PdfPCell[] {a, b}) {
            c.setPadding(6);
            c.setBorderColor(new Color(226, 232, 240));
            if (total) c.setBackgroundColor(new Color(238, 240, 255));
        }
        t.addCell(a);
        t.addCell(b);
    }

    private static String opcional(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static DesistimientoDtos.Response respuesta(ResultSet rs) throws SQLException {
        Date devolucion = rs.getDate("fecha_devolucion");
        Timestamp revisado = rs.getTimestamp("revisado_en");
        return new DesistimientoDtos.Response(rs.getLong("id"), rs.getString("codigo"), rs.getLong("contrato_id"),
                rs.getString("contrato"), rs.getString("cliente"), rs.getString("proyecto"), rs.getString("lote"),
                rs.getString("origen"), rs.getString("motivo"), rs.getBigDecimal("total_pagado"),
                rs.getBigDecimal("porcentaje_penalidad"), rs.getBigDecimal("valor_penalidad"),
                rs.getBigDecimal("otros_descuentos"), rs.getBigDecimal("valor_devolver"),
                devolucion == null ? null : devolucion.toLocalDate(), rs.getString("observaciones"), rs.getString("estado"),
                rs.getLong("solicitado_por"), rs.getObject("revisado_por", Long.class),
                revisado == null ? null : revisado.toLocalDateTime(), rs.getString("comentario_revision"),
                rs.getInt("cuotas_canceladas"), rs.getInt("comisiones_anuladas"), rs.getLong("version"),
                rs.getTimestamp("creado_en").toLocalDateTime());
    }

    private static final String SELECT = """
            SELECT d.*, c.codigo contrato, CONCAT_WS(' ', pe.nombres, pe.apellidos) cliente, p.nombre proyecto, l.codigo lote
            FROM desistimientos d
            JOIN contratos c ON c.id = d.contrato_id
            JOIN proyectos p ON p.id = d.proyecto_id
            JOIN lotes l ON l.id = d.lote_id
            JOIN clientes cl ON cl.id = d.cliente_id
            JOIN personas pe ON pe.id = cl.persona_id
            """;
}
