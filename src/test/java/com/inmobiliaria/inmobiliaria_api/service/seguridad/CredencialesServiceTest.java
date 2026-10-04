package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.dto.seguridad.CambioPasswordRequest;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredencialesServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionService sessionService;
    @Mock
    private AuditoriaRegistroService auditoria;
    @InjectMocks
    private CredencialesService service;

    @Test
    void cambiaLaPasswordYRevocaTodasLasSesiones() {
        Usuario usuario = usuario();
        CambioPasswordRequest request = new CambioPasswordRequest(
                "Actual#2026Segura",
                "Nueva#2026Segura",
                "Nueva#2026Segura"
        );
        when(usuarioRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Actual#2026Segura", "hash-actual")).thenReturn(true);
        when(passwordEncoder.matches("Nueva#2026Segura", "hash-actual")).thenReturn(false);
        when(passwordEncoder.encode("Nueva#2026Segura")).thenReturn("hash-nuevo");

        service.cambiarPassword(7L, request);

        assertThat(usuario.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(usuario.isRequiereCambioPassword()).isFalse();
        assertThat(usuario.getTokenVersion()).isEqualTo(1L);
        verify(usuarioRepository).saveAndFlush(usuario);
        verify(sessionService).revocarTodas(
                7L,
                "Cambio de contraseña realizado por el usuario"
        );
        verify(auditoria).registrar(
                any(), any(), anyString(), anyString(), anyString(),
                any(), anyString(), any(), any()
        );
    }

    @Test
    void rechazaUnaPasswordActualIncorrectaSinModificarLaCuenta() {
        Usuario usuario = usuario();
        CambioPasswordRequest request = new CambioPasswordRequest(
                "Incorrecta#2026",
                "Nueva#2026Segura",
                "Nueva#2026Segura"
        );
        when(usuarioRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Incorrecta#2026", "hash-actual")).thenReturn(false);

        assertThatThrownBy(() -> service.cambiarPassword(7L, request))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La contraseña actual no es correcta");

        assertThat(usuario.getPasswordHash()).isEqualTo("hash-actual");
        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(sessionService, never()).revocarTodas(any(), anyString());
    }

    private Usuario usuario() {
        return new Usuario(
                1L,
                2L,
                "fernandoj",
                "fernando@nexora.test",
                "hash-actual"
        );
    }
}
