package com.inmobiliaria.inmobiliaria_api.entity.cliente;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClienteTest {

    @Test
    void creaPerfilActivoConDatosComerciales() {
        Cliente cliente = new Cliente(1L, 5L, "CLI-001", "SALA DE VENTAS",
                "Interesado en lote esquinero");

        assertThat(cliente.getEstado()).isEqualTo("ACTIVO");
        assertThat(cliente.getOrigen()).isEqualTo("SALA DE VENTAS");
        assertThat(cliente.getFechaAlta()).isNotNull();
    }

    @Test
    void rechazaEstadosFueraDelCatalogo() {
        Cliente cliente = new Cliente(1L, 5L, "CLI-001", null, null);

        assertThatThrownBy(() -> cliente.cambiarEstado("ELIMINADO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
