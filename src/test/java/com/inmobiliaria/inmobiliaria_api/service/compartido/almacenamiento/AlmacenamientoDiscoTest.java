package com.inmobiliaria.inmobiliaria_api.service.compartido.almacenamiento;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlmacenamientoDiscoTest {

    @TempDir
    Path carpeta;

    @Test
    void guardaAbreYBorra() throws Exception {
        AlmacenamientoDisco disco = new AlmacenamientoDisco(carpeta.toString());
        disco.guardar("3/7/a.pdf", "hola".getBytes(StandardCharsets.UTF_8), "application/pdf");

        assertThat(disco.abrir("3/7/a.pdf").getContentAsByteArray()).asString(StandardCharsets.UTF_8).isEqualTo("hola");
        disco.eliminarSilencioso("3/7/a.pdf");
        assertThatThrownBy(() -> disco.abrir("3/7/a.pdf")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void noSaleDeLaCarpeta() {
        AlmacenamientoDisco disco = new AlmacenamientoDisco(carpeta.toString());
        assertThatThrownBy(() -> disco.guardar("../fuera.pdf", new byte[] {1}, "application/pdf"))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
