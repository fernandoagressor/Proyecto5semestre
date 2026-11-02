package com.inmobiliaria.inmobiliaria_api.service.compartido.exportacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ExportadorTablasTest {

    private final ExportadorTablas exportador = new ExportadorTablas();
    private final Tabla tabla = new Tabla("Ventas & cartera", "Octubre",
            List.of("Cliente", "Fecha", "Valor"),
            List.of(List.of("=HYPERLINK(\"x\")", LocalDate.of(2026, 10, 5), new BigDecimal("1500000.00")),
                    java.util.Arrays.asList("Ana; Pérez", null, 20)),
            List.of(false, false, true));

    @Test
    void csvConBomYProteccionContraFormulas() {
        String csv = new String(exportador.exportar(tabla, Formato.CSV, "Demo"), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Cliente;Fecha;Valor\r\n");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\";05/10/2026;1500000");
        assertThat(csv).contains("\"Ana; Pérez\";;20");
    }

    @Test
    void xlsxEsUnLibroValidoConLasFilas() throws Exception {
        Map<String, String> partes = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(exportador.exportar(tabla, Formato.XLSX, "Demo")))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                partes.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(partes).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml");
        assertThat(partes.get("xl/workbook.xml")).contains("name=\"Ventas &amp; cartera\"");
        String hoja = partes.get("xl/worksheets/sheet1.xml");
        assertThat(hoja).contains("<v>1500000.00</v>").contains("=HYPERLINK(&quot;x&quot;)").contains("<v>46300</v>");
    }

    @Test
    void pdfValido() {
        byte[] pdf = exportador.exportar(tabla, Formato.PDF, "Demo");
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(800);
    }

    @Test
    void pesosEnFormatoColombiano() {
        assertThat(ExportadorTablas.pesos(new BigDecimal("1500000"))).isEqualTo("$ 1.500.000");
    }
}
