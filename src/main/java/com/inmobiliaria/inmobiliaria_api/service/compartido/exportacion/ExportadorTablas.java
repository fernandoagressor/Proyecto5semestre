package com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion;

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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

/** Convierte una {@link Tabla} en CSV (con BOM y protección contra fórmulas), XLSX o PDF. */
@Component
public class ExportadorTablas {

    static final Locale CO = Locale.forLanguageTag("es-CO");
    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final Color INDIGO = new Color(79, 70, 229);
    static final Color GRIS = new Color(100, 116, 139);
    static final Color FONDO = new Color(244, 246, 251);

    public byte[] exportar(Tabla tabla, Formato formato, String empresa) {
        return switch (formato) {
            case CSV -> csv(tabla);
            case XLSX -> xlsx(tabla);
            case PDF -> pdf(tabla, empresa);
        };
    }

    // ---------- CSV ----------

    byte[] csv(Tabla tabla) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(";", tabla.columnas().stream().map(ExportadorTablas::csvCelda).toList())).append("\r\n");
        for (List<Object> fila : tabla.filas()) {
            sb.append(String.join(";", fila.stream().map(v -> csvCelda(texto(v, false))).toList())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvCelda(String valor) {
        String v = valor == null ? "" : valor;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")
                ? "\"" + v.replace("\"", "\"\"") + "\""
                : v;
    }

    // ---------- XLSX (SpreadsheetML mínimo, sin dependencias) ----------

    byte[] xlsx(Tabla tabla) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            entrada(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""");
            entrada(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            entrada(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets></workbook>""".formatted(xml(nombreHoja(tabla.titulo()))));
            entrada(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            // Estilos: 0 normal, 1 encabezado en negrita, 2 pesos sin decimales, 3 fecha.
            entrada(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <numFmts count="2"><numFmt numFmtId="164" formatCode="&quot;$&quot; #,##0"/><numFmt numFmtId="165" formatCode="dd/mm/yyyy"/></numFmts>
                    <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                    <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                    <borders count="1"><border/></borders>
                    <cellStyleXfs count="1"><xf/></cellStyleXfs>
                    <cellXfs count="4"><xf/><xf fontId="1" applyFont="1"/><xf numFmtId="164" applyNumberFormat="1"/><xf numFmtId="165" applyNumberFormat="1"/></cellXfs>
                    </styleSheet>""");
            StringBuilder hoja = new StringBuilder("""
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""");
            hoja.append("<row r=\"1\">");
            for (String columna : tabla.columnas()) {
                hoja.append("<c t=\"inlineStr\" s=\"1\"><is><t>").append(xml(columna)).append("</t></is></c>");
            }
            hoja.append("</row>");
            int r = 2;
            for (List<Object> fila : tabla.filas()) {
                hoja.append("<row r=\"").append(r++).append("\">");
                for (int i = 0; i < fila.size(); i++) {
                    Object v = fila.get(i);
                    if (v == null) {
                        hoja.append("<c/>");
                    } else if (v instanceof Number n) {
                        hoja.append("<c s=\"").append(tabla.moneda().get(i) ? 2 : 0).append("\"><v>")
                                .append(new BigDecimal(n.toString()).toPlainString()).append("</v></c>");
                    } else if (v instanceof LocalDate d) {
                        long serial = d.toEpochDay() + 25569;
                        hoja.append("<c s=\"3\"><v>").append(serial).append("</v></c>");
                    } else {
                        hoja.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(texto(v, false)))
                                .append("</t></is></c>");
                    }
                }
                hoja.append("</row>");
            }
            hoja.append("</sheetData></worksheet>");
            entrada(zip, "xl/worksheets/sheet1.xml", hoja.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    private static void entrada(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String nombreHoja(String titulo) {
        String limpio = titulo == null ? "Hoja1" : titulo.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return limpio.isEmpty() ? "Hoja1" : limpio.substring(0, Math.min(31, limpio.length()));
    }

    private static String xml(String valor) {
        StringBuilder sb = new StringBuilder();
        for (char c : (valor == null ? "" : valor).toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    // ---------- PDF ----------

    byte[] pdf(Tabla tabla, String empresa) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        boolean horizontal = tabla.columnas().size() > 5;
        Document doc = new Document(horizontal ? PageSize.A4.rotate() : PageSize.A4, 32, 32, 36, 36);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();
            encabezado(doc, empresa, tabla.titulo(), tabla.subtitulo());
            PdfPTable t = new PdfPTable(tabla.columnas().size());
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            Font cab = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
            for (String c : tabla.columnas()) {
                PdfPCell celda = new PdfPCell(new Phrase(c, cab));
                celda.setBackgroundColor(INDIGO);
                celda.setPadding(5);
                celda.setBorderColor(INDIGO);
                t.addCell(celda);
            }
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);
            int i = 0;
            for (List<Object> fila : tabla.filas()) {
                Color fondo = i++ % 2 == 0 ? Color.WHITE : FONDO;
                for (int c = 0; c < fila.size(); c++) {
                    Object v = fila.get(c);
                    PdfPCell celda = new PdfPCell(new Phrase(texto(v, tabla.moneda().get(c)), normal));
                    celda.setPadding(4);
                    celda.setBorderColor(new Color(226, 232, 240));
                    celda.setBackgroundColor(fondo);
                    if (v instanceof Number) celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    t.addCell(celda);
                }
            }
            if (tabla.filas().isEmpty()) {
                PdfPCell vacia = new PdfPCell(new Phrase("Sin registros para los filtros elegidos.", normal));
                vacia.setColspan(tabla.columnas().size());
                vacia.setPadding(8);
                t.addCell(vacia);
            }
            doc.add(t);
            Paragraph pie = new Paragraph(tabla.filas().size() + " registros", FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS));
            pie.setSpacingBefore(6);
            doc.add(pie);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            doc.close();
        }
        return salida.toByteArray();
    }

    /** Encabezado común de los PDF: empresa, título, subtítulo y fecha de generación. */
    public static void encabezado(Document doc, String empresa, String titulo, String subtitulo) throws DocumentException {
        Paragraph emp = new Paragraph(empresa == null ? "" : empresa.toUpperCase(CO),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, INDIGO));
        doc.add(emp);
        Paragraph tit = new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK));
        tit.setSpacingBefore(2);
        doc.add(tit);
        String generado = "Generado el " + LocalDateTime.now(ZoneId.of("America/Bogota")).format(FECHA_HORA);
        Paragraph sub = new Paragraph((subtitulo == null || subtitulo.isBlank() ? "" : subtitulo + " · ") + generado,
                FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS));
        sub.setSpacingAfter(10);
        doc.add(sub);
    }

    public static String pesos(Object v) {
        if (v == null) return "";
        BigDecimal n = new BigDecimal(v.toString());
        NumberFormat f = NumberFormat.getNumberInstance(CO);
        f.setMaximumFractionDigits(0);
        return "$ " + f.format(n);
    }

    static String texto(Object v, boolean moneda) {
        if (v == null) return "";
        if (moneda && v instanceof Number) return pesos(v);
        if (v instanceof BigDecimal b) return b.stripTrailingZeros().toPlainString();
        if (v instanceof LocalDate d) return d.format(FECHA);
        if (v instanceof LocalDateTime d) return d.format(FECHA_HORA);
        return v.toString();
    }
}
