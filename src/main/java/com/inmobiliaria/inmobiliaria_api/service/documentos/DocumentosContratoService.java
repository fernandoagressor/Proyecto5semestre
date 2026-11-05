package com.inmobiliaria.inmobiliaria_api.service.documentos;

import static com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.ExportadorTablas.encabezado;
import static com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion.ExportadorTablas.pesos;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectoService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documentos PDF de un contrato: estado de cuenta, paz y salvo y cobro de intereses de mora.
 * Los genera al vuelo con los datos vigentes (no se guardan) y deja rastro en la auditoría.
 */
@Service
public class DocumentosContratoService {

    public record Pdf(String nombre, byte[] contenido) { }

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-CO"));
    private static final Color GRIS = new Color(100, 116, 139);
    private static final Color INDIGO = new Color(79, 70, 229);
    private static final Color BORDE = new Color(226, 232, 240);

    private final JdbcTemplate jdbc;
    private final AlcanceProyectoService alcance;
    private final AuditoriaRegistroService auditoria;

    public DocumentosContratoService(JdbcTemplate jdbc, AlcanceProyectoService alcance, AuditoriaRegistroService auditoria) {
        this.jdbc = jdbc;
        this.alcance = alcance;
        this.auditoria = auditoria;
    }

    record Contrato(Long id, Long proyectoId, String codigo, String estado, String cliente, String documento,
                    String proyecto, String lote, BigDecimal area, BigDecimal valorTotal, LocalDate firma,
                    String empresa, String nit, String empresaTelefono, String empresaCorreo, Long clienteId) { }

    record Cuota(int numero, LocalDate vence, BigDecimal valor, BigDecimal pagado, BigDecimal saldo,
                 BigDecimal mora, int diasMora, String estado) { }

    // ---------- Puntos de entrada ----------

    @Transactional(readOnly = true)
    public Pdf estadoCuenta(ContextoUsuario ctx, Long contratoId) {
        Contrato c = contratoInterno(ctx, contratoId);
        return registrar(ctx, c, "ESTADO_CUENTA_GENERADO", "estado-de-cuenta", estadoCuentaPdf(c));
    }

    /** Estado de cuenta pedido por el propio cliente desde su portal. */
    @Transactional(readOnly = true)
    public Pdf estadoCuentaCliente(ContextoUsuario ctx, Long contratoId) {
        Long clienteId = jdbc.query("SELECT id FROM clientes WHERE usuario_id = ? AND empresa_id = ? AND estado = 'ACTIVO'",
                rs -> rs.next() ? rs.getLong(1) : null, ctx.usuarioId(), ctx.empresaId());
        if (clienteId == null) {
            throw new AccesoDenegadoException("La operación está disponible únicamente en el portal del cliente");
        }
        Contrato c = contrato(ctx.empresaId(), contratoId);
        Integer propio = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contratos x WHERE x.id = ? AND (x.cliente_principal_id = ?
                  OR EXISTS (SELECT 1 FROM contrato_clientes cc WHERE cc.contrato_id = x.id AND cc.cliente_id = ?))
                """, Integer.class, contratoId, clienteId, clienteId);
        if (propio == null || propio == 0) {
            throw new RecursoNoEncontradoException("El contrato no existe");
        }
        return registrar(ctx, c, "ESTADO_CUENTA_GENERADO", "estado-de-cuenta", estadoCuentaPdf(c));
    }

    @Transactional(readOnly = true)
    public Pdf pazYSalvo(ContextoUsuario ctx, Long contratoId) {
        Contrato c = contratoInterno(ctx, contratoId);
        List<Cuota> cuotas = cuotas(c.id());
        if (!List.of("VIGENTE", "FINALIZADO").contains(c.estado()) || cuotas.isEmpty()) {
            throw new ReglaNegocioException("El paz y salvo solo se expide para contratos vigentes o finalizados con plan de pagos");
        }
        BigDecimal pendiente = cuotas.stream().filter(q -> abierta(q.estado()))
                .map(q -> q.saldo().add(q.mora())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pendiente.signum() > 0) {
            throw new ReglaNegocioException("El contrato tiene un saldo pendiente de " + pesos(pendiente));
        }
        return registrar(ctx, c, "PAZ_Y_SALVO_GENERADO", "paz-y-salvo", pazYSalvoPdf(c, cuotas));
    }

    @Transactional(readOnly = true)
    public Pdf cobroMora(ContextoUsuario ctx, Long contratoId) {
        Contrato c = contratoInterno(ctx, contratoId);
        List<Cuota> vencidas = cuotas(c.id()).stream().filter(q -> "VENCIDA".equals(q.estado())
                || (abierta(q.estado()) && q.vence().isBefore(LocalDate.now(BOGOTA)))).toList();
        if (vencidas.isEmpty()) {
            throw new ReglaNegocioException("El contrato no tiene cuotas vencidas");
        }
        return registrar(ctx, c, "COBRO_MORA_GENERADO", "cobro-de-mora", cobroMoraPdf(c, vencidas));
    }

    // ---------- PDF ----------

    private byte[] estadoCuentaPdf(Contrato c) {
        List<Cuota> cuotas = cuotas(c.id());
        BigDecimal pagado = suma(cuotas.stream().map(Cuota::pagado).toList());
        BigDecimal saldo = suma(cuotas.stream().filter(q -> abierta(q.estado())).map(q -> q.saldo().add(q.mora())).toList());
        BigDecimal vencido = suma(cuotas.stream().filter(q -> "VENCIDA".equals(q.estado())).map(q -> q.saldo().add(q.mora())).toList());
        return documento(c, "Estado de cuenta", doc -> {
            datosContrato(doc, c);
            resumen(doc, new String[] {"Valor del contrato", "Pagado", "Saldo por pagar", "Vencido"},
                    new BigDecimal[] {c.valorTotal(), pagado, saldo, vencido});
            PdfPTable t = tabla(new float[] {0.6f, 1.2f, 1.4f, 1.4f, 1.4f, 1.2f, 1.2f},
                    "Cuota", "Vence", "Valor", "Pagado", "Saldo", "Mora", "Estado");
            for (Cuota q : cuotas) {
                celdas(t, q.numero() == 0 ? "Inicial" : String.valueOf(q.numero()), q.vence().format(FECHA), pesos(q.valor()), pesos(q.pagado()),
                        pesos(q.saldo()), pesos(q.mora()), estadoCuota(q.estado()));
            }
            doc.add(t);
            nota(doc, "Los valores de mora se calculan a la fecha de expedición y pueden cambiar con cada día de atraso.");
        });
    }

    private byte[] pazYSalvoPdf(Contrato c, List<Cuota> cuotas) {
        BigDecimal pagado = suma(cuotas.stream().map(Cuota::pagado).toList());
        return documento(c, "Certificado de paz y salvo", doc -> {
            Font texto = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
            Paragraph p = new Paragraph(c.empresa() + (c.nit() == null ? "" : ", NIT " + c.nit())
                    + ", certifica que " + c.cliente() + (c.documento() == null ? "" : ", identificado(a) con " + c.documento())
                    + ", se encuentra a paz y salvo por todo concepto con el contrato " + c.codigo()
                    + " correspondiente al lote " + c.lote() + " del proyecto " + c.proyecto()
                    + ", por un valor total de " + pesos(c.valorTotal()) + ", del cual se recibieron pagos por "
                    + pesos(pagado) + " en " + cuotas.size() + " cuotas.", texto);
            p.setAlignment(Element.ALIGN_JUSTIFIED);
            p.setLeading(17);
            p.setSpacingBefore(18);
            doc.add(p);
            Paragraph fecha = new Paragraph("Se expide a solicitud del interesado el " + LocalDate.now(BOGOTA).format(FECHA_LARGA) + ".", texto);
            fecha.setSpacingBefore(14);
            doc.add(fecha);
            Paragraph firma = new Paragraph("\n\n\n______________________________\n" + c.empresa() + "\nÁrea de cartera", texto);
            firma.setSpacingBefore(30);
            doc.add(firma);
        });
    }

    private byte[] cobroMoraPdf(Contrato c, List<Cuota> vencidas) {
        BigDecimal capital = suma(vencidas.stream().map(Cuota::saldo).toList());
        BigDecimal mora = suma(vencidas.stream().map(Cuota::mora).toList());
        return documento(c, "Cuenta de cobro por mora", doc -> {
            datosContrato(doc, c);
            Font texto = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Paragraph p = new Paragraph("Señor(a) " + c.cliente() + ": a la fecha registramos " + vencidas.size()
                    + (vencidas.size() == 1 ? " cuota vencida" : " cuotas vencidas")
                    + " en su contrato. Le invitamos a ponerse al día para evitar que sigan corriendo intereses de mora.", texto);
            p.setSpacingAfter(10);
            doc.add(p);
            PdfPTable t = tabla(new float[] {0.7f, 1.2f, 1f, 1.5f, 1.5f, 1.5f},
                    "Cuota", "Venció", "Días", "Capital", "Interés de mora", "Total");
            for (Cuota q : vencidas) {
                celdas(t, q.numero() == 0 ? "Inicial" : String.valueOf(q.numero()), q.vence().format(FECHA), String.valueOf(q.diasMora()),
                        pesos(q.saldo()), pesos(q.mora()), pesos(q.saldo().add(q.mora())));
            }
            doc.add(t);
            resumen(doc, new String[] {"Capital vencido", "Intereses de mora", "Total a pagar"},
                    new BigDecimal[] {capital, mora, capital.add(mora)});
            nota(doc, "Si ya realizó el pago, por favor omita este aviso. Para acuerdos de pago comuníquese con "
                    + c.empresa() + (c.empresaTelefono() == null ? "" : " al " + c.empresaTelefono())
                    + (c.empresaCorreo() == null ? "" : " o al correo " + c.empresaCorreo()) + ".");
        });
    }

    // ---------- Piezas ----------

    interface Contenido {
        void escribir(Document doc) throws DocumentException;
    }

    private byte[] documento(Contrato c, String titulo, Contenido contenido) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.addTitle(titulo + " " + c.codigo());
            doc.open();
            encabezado(doc, c.empresa(), titulo, "Contrato " + c.codigo());
            contenido.escribir(doc);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }

    private static void datosContrato(Document doc, Contrato c) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[] {1, 2, 1, 2});
        t.setWidthPercentage(100);
        t.setSpacingAfter(10);
        par(t, "Cliente", c.cliente());
        par(t, "Documento", c.documento() == null ? "—" : c.documento());
        par(t, "Proyecto", c.proyecto());
        par(t, "Lote", c.lote() + " · " + c.area().stripTrailingZeros().toPlainString() + " m²");
        par(t, "Firma", c.firma() == null ? "—" : c.firma().format(FECHA));
        par(t, "Estado", c.estado());
        doc.add(t);
    }

    private static void par(PdfPTable t, String etiqueta, String valor) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, FontFactory.getFont(FontFactory.HELVETICA, 8.5f, GRIS)));
        PdfPCell b = new PdfPCell(new Phrase(valor, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.BLACK)));
        for (PdfPCell x : new PdfPCell[] {a, b}) {
            x.setBorder(0);
            x.setPadding(3);
        }
        t.addCell(a);
        t.addCell(b);
    }

    private static void resumen(Document doc, String[] etiquetas, BigDecimal[] valores) throws DocumentException {
        PdfPTable t = new PdfPTable(etiquetas.length);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setSpacingAfter(12);
        for (int i = 0; i < etiquetas.length; i++) {
            PdfPCell celda = new PdfPCell();
            celda.addElement(new Paragraph(etiquetas[i], FontFactory.getFont(FontFactory.HELVETICA, 8.5f, GRIS)));
            celda.addElement(new Paragraph(pesos(valores[i]), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12,
                    i == etiquetas.length - 1 ? INDIGO : Color.BLACK)));
            celda.setBorderColor(BORDE);
            celda.setPadding(8);
            t.addCell(celda);
        }
        doc.add(t);
    }

    private static PdfPTable tabla(float[] anchos, String... columnas) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        for (String c : columnas) {
            PdfPCell celda = new PdfPCell(new Phrase(c, f));
            celda.setBackgroundColor(INDIGO);
            celda.setBorderColor(INDIGO);
            celda.setPadding(5);
            t.addCell(celda);
        }
        return t;
    }

    private static void celdas(PdfPTable t, String... valores) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);
        for (int i = 0; i < valores.length; i++) {
            PdfPCell celda = new PdfPCell(new Phrase(valores[i], f));
            celda.setBorderColor(BORDE);
            celda.setPadding(4);
            if (valores[i].startsWith("$")) celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(celda);
        }
    }

    private static void nota(Document doc, String texto) throws DocumentException {
        Paragraph p = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA, 8.5f, GRIS));
        p.setSpacingBefore(10);
        doc.add(p);
    }

    // ---------- Datos ----------

    private Contrato contratoInterno(ContextoUsuario ctx, Long contratoId) {
        Contrato c = contrato(ctx.empresaId(), contratoId);
        AlcanceProyectos a = alcance.obtener(ctx.usuarioId(), ctx.empresaId());
        if (!a.empresaCompleta() && !a.proyectos().contains(c.proyectoId())) {
            throw new AccesoDenegadoException("No tiene acceso al proyecto de este contrato");
        }
        return c;
    }

    private Contrato contrato(Long empresaId, Long contratoId) {
        List<Contrato> lista = jdbc.query("""
                SELECT c.id, c.proyecto_id, c.codigo, c.estado, CONCAT_WS(' ', pe.nombres, pe.apellidos) cliente,
                       CONCAT(pe.tipo_documento, ' ', pe.numero_documento) documento, p.nombre proyecto, l.codigo lote,
                       l.area_m2, c.valor_total, c.fecha_firma, e.nombre empresa, e.nit, e.telefono, e.correo, cl.id cliente_id
                FROM contratos c
                JOIN proyectos p ON p.id = c.proyecto_id
                JOIN lotes l ON l.id = c.lote_id
                JOIN clientes cl ON cl.id = c.cliente_principal_id
                JOIN personas pe ON pe.id = cl.persona_id
                JOIN empresas e ON e.id = c.empresa_id
                WHERE c.id = ? AND c.empresa_id = ?
                """, (rs, n) -> {
                    java.sql.Date firma = rs.getDate("fecha_firma");
                    return new Contrato(rs.getLong("id"), rs.getLong("proyecto_id"), rs.getString("codigo"),
                            rs.getString("estado"), rs.getString("cliente"), rs.getString("documento"),
                            rs.getString("proyecto"), rs.getString("lote"), rs.getBigDecimal("area_m2"),
                            rs.getBigDecimal("valor_total"), firma == null ? null : firma.toLocalDate(),
                            rs.getString("empresa"), rs.getString("nit"), rs.getString("telefono"),
                            rs.getString("correo"), rs.getLong("cliente_id"));
                }, contratoId, empresaId);
        if (lista.isEmpty()) {
            throw new RecursoNoEncontradoException("El contrato no existe");
        }
        return lista.get(0);
    }

    private List<Cuota> cuotas(Long contratoId) {
        return jdbc.query("""
                SELECT q.numero, q.fecha_vencimiento, q.valor_programado, q.valor_pagado, q.saldo, q.interes_mora,
                       q.dias_mora, q.estado
                FROM cuotas q JOIN planes_pago pp ON pp.id = q.plan_pago_id AND pp.estado = 'ACTIVO'
                WHERE q.contrato_id = ? ORDER BY q.numero
                """, (rs, n) -> new Cuota(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getBigDecimal(3),
                rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7), rs.getString(8)), contratoId);
    }

    private Pdf registrar(ContextoUsuario ctx, Contrato c, String accion, String nombre, byte[] pdf) {
        auditoria.registrar(ctx, c.proyectoId(), "CARTERA", accion, "CONTRATO", c.id(),
                "Documento " + nombre + " del contrato " + c.codigo(), null, null);
        return new Pdf(nombre + "-" + c.codigo() + ".pdf", pdf);
    }

    private static boolean abierta(String estado) {
        return List.of("PENDIENTE", "PARCIAL", "VENCIDA").contains(estado);
    }

    private static BigDecimal suma(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String estadoCuota(String estado) {
        return switch (estado) {
            case "PAGADA" -> "Pagada";
            case "PARCIAL" -> "Parcial";
            case "VENCIDA" -> "Vencida";
            case "CONDONADA" -> "Condonada";
            case "CANCELADA" -> "Cancelada";
            default -> "Pendiente";
        };
    }
}
