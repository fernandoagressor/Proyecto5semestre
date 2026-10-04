package com.inmobiliaria.inmobiliaria_api.security;

import com.inmobiliaria.inmobiliaria_api.entity.seguridad.SesionUsuario;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoMultiempresaTest {

    @Test
    void conservaLaEmpresaSeleccionadaEnLaSesionRenovable() {
        LocalDateTime ahora = LocalDateTime.of(2026, 8, 10, 9, 0);
        SesionUsuario sesion = new SesionUsuario(
                1L,
                5L,
                "a".repeat(64),
                "127.0.0.1",
                "JUnit",
                ahora,
                ahora.plusDays(1)
        );

        sesion.cambiarEmpresaContexto(9L);

        assertThat(sesion.getEmpresaContextoId()).isEqualTo(9L);
        assertThat(sesion.estaVigente(ahora.plusHours(1))).isTrue();
    }

    @Test
    void cambiaElClaimDeEmpresaSinPerderAutoridadesGlobales() {
        Usuario usuario = new Usuario(
                2L,
                3L,
                "superadmin",
                "root@nexora.test",
                "hash"
        );
        UsuarioPrincipal principal = UsuarioPrincipal.from(
                usuario,
                List.of("ROLE_SUPER_ADMIN", "EMPRESAS_CREAR")
        );

        UsuarioPrincipal cambiado = principal.conEmpresaContexto(11L);

        assertThat(cambiado.empresaId()).isEqualTo(11L);
        assertThat(cambiado.usuarioId()).isEqualTo(principal.usuarioId());
        assertThat(cambiado.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "EMPRESAS_CREAR");
    }

    @Test
    void unaEmpresaNoHabilitadaBloqueaUsuariosOrdinarios() {
        Usuario usuario = new Usuario(
                2L,
                3L,
                "asesor",
                "asesor@nexora.test",
                "hash"
        );

        UsuarioPrincipal principal = UsuarioPrincipal.from(
                usuario,
                List.of("ROLE_ASESOR_COMERCIAL"),
                false
        );

        assertThat(principal.isEnabled()).isFalse();
    }
}
