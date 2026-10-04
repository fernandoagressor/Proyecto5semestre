package com.inmobiliaria.inmobiliaria_api.service.inventario;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlcanceProyectosTest {

    @Test
    void permiteSoloProyectosExpresamenteAsignados() {
        AlcanceProyectos alcance = new AlcanceProyectos(
                false,
                false,
                Set.of(10L, 20L)
        );

        assertThat(alcance.permite(10L, null, 5L)).isTrue();
        assertThat(alcance.permite(30L, null, 5L)).isFalse();
    }

    @Test
    void permiteProyectoPropioCuandoUsuarioEsGerente() {
        AlcanceProyectos alcance = new AlcanceProyectos(
                false,
                true,
                Set.of()
        );

        assertThat(alcance.permite(30L, 5L, 5L)).isTrue();
        assertThat(alcance.permite(30L, 8L, 5L)).isFalse();
    }

    @Test
    void alcanceEmpresaPermiteTodosLosProyectos() {
        AlcanceProyectos alcance = new AlcanceProyectos(
                true,
                false,
                Set.of()
        );

        assertThat(alcance.permite(999L, null, 5L)).isTrue();
    }
}
