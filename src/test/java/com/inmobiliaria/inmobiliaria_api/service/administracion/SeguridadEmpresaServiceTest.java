package com.inmobiliaria.inmobiliaria_api.service.administracion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Regla de delegación: nadie otorga ni administra permisos que no tiene. */
class SeguridadEmpresaServiceTest {

    private static final ContextoUsuario ACTOR = new ContextoUsuario(7L, 3L);

    private final AdministracionUsuarioService usuarios = mock(AdministracionUsuarioService.class);
    private final AdministracionRolService roles = mock(AdministracionRolService.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final SeguridadEmpresaService service =
            new SeguridadEmpresaService(usuarios, roles, usuarioRepository, jdbc);

    @BeforeEach
    void permisosDelActor() {
        when(usuarioRepository.findPermissionCodes(7L)).thenReturn(List.of("USUARIOS_VER", "ROLES_CREAR", "LOTES_VER"));
    }

    private void permisosConsultados(List<String> codigos) {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class))).thenReturn(codigos);
    }

    @Test
    void noCreaRolesConPermisosQueElActorNoTiene() {
        permisosConsultados(List.of("LOTES_VER", "PAGOS_REVERSAR"));
        var request = new AdministracionDtos.RolGuardarRequest("X", "X", null, true, List.of(1L, 2L));

        assertThatThrownBy(() -> service.crearRol(ACTOR, request))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("PAGOS_REVERSAR");
        verifyNoInteractions(roles);
    }

    @Test
    void creaRolesConPermisosPropios() {
        permisosConsultados(List.of("LOTES_VER"));
        var request = new AdministracionDtos.RolGuardarRequest("X", "X", null, true, List.of(1L));

        service.crearRol(ACTOR, request);

        verify(roles).crear(7L, 3L, request);
    }

    @Test
    void elActorNoSeBloqueaASiMismo() {
        var request = new AdministracionDtos.CambioEstadoUsuarioRequest("BLOQUEADO", "prueba", 0L);

        assertThatThrownBy(() -> service.cambiarEstadoUsuario(ACTOR, 7L, request))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(usuarios);
    }

    @Test
    void noAdministraAUsuariosConMasPermisos() {
        when(usuarioRepository.findPermissionCodes(20L)).thenReturn(List.of("USUARIOS_VER", "EMPRESA_CONFIGURAR"));
        var request = new AdministracionDtos.CambioEstadoUsuarioRequest("BLOQUEADO", "prueba", 0L);

        assertThatThrownBy(() -> service.cambiarEstadoUsuario(ACTOR, 20L, request))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("EMPRESA_CONFIGURAR");
        verifyNoInteractions(usuarios);
    }

    @Test
    void administraAUsuariosConPermisosIncluidosEnLosSuyos() {
        when(usuarioRepository.findPermissionCodes(21L)).thenReturn(List.of("LOTES_VER"));
        var request = new AdministracionDtos.CambioEstadoUsuarioRequest("BLOQUEADO", "prueba", 0L);

        service.cambiarEstadoUsuario(ACTOR, 21L, request);

        verify(usuarios).cambiarEstado(7L, 3L, 21L, request);
    }
}
