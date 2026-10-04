package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.config.BootstrapAdminProperties;
import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.identidad.PersonaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminService implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BootstrapAdminService.class);

    private final BootstrapAdminProperties properties;
    private final EmpresaRepository empresaRepository;
    private final PersonaRepository personaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public BootstrapAdminService(
            BootstrapAdminProperties properties,
            EmpresaRepository empresaRepository,
            PersonaRepository personaRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.properties = properties;
        this.empresaRepository = empresaRepository;
        this.personaRepository = personaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        validar();
        if (usuarioRepository.findByIdentificador(properties.email()).isPresent()
                || usuarioRepository.findByIdentificador(properties.username()).isPresent()) {
            LOGGER.info("El administrador inicial ya existe; bootstrap omitido");
            return;
        }

        Empresa empresa = empresaRepository.findByCodigo(properties.empresaCodigo())
                .orElseGet(() -> empresaRepository.save(new Empresa(
                        properties.empresaCodigo(),
                        properties.empresaNombre(),
                        properties.empresaNit()
                )));
        Persona persona = personaRepository
                .findByTipoDocumentoAndNumeroDocumento(
                        "CC",
                        properties.documento()
                )
                .orElseGet(() -> personaRepository.save(new Persona(
                        "CC",
                        properties.documento(),
                        properties.nombres(),
                        properties.apellidos(),
                        properties.email()
                )));
        Usuario usuario = usuarioRepository.save(new Usuario(
                empresa.getId(),
                persona.getId(),
                properties.username(),
                properties.email(),
                passwordEncoder.encode(properties.password())
        ));

        int asignaciones = jdbcTemplate.update("""
                INSERT INTO usuario_roles
                    (usuario_id, rol_id, principal, creado_en, actualizado_en)
                SELECT ?, r.id, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                FROM roles r
                WHERE r.codigo = 'SUPER_ADMIN'
                  AND r.empresa_id IS NULL
                """, usuario.getId());
        if (asignaciones != 1) {
            throw new IllegalStateException(
                    "No fue posible asignar el rol SUPER_ADMIN"
            );
        }
        int alcances = jdbcTemplate.update("""
                INSERT INTO usuario_alcances
                    (usuario_id, empresa_id, proyecto_id, tipo_alcance,
                     vigente_desde, creado_en, actualizado_en)
                VALUES (?, ?, NULL, 'EMPRESA',
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6))
                """, usuario.getId(), empresa.getId());
        if (alcances != 1) {
            throw new IllegalStateException(
                    "No fue posible asignar el alcance de empresa al administrador"
            );
        }
        LOGGER.info(
                "SUPER_ADMIN inicial creado para la empresa {}",
                empresa.getCodigo()
        );
    }

    private void validar() {
        List<String> camposFaltantes = Stream.of(
                valor("BOOTSTRAP_EMPRESA_CODIGO", properties.empresaCodigo()),
                valor("BOOTSTRAP_EMPRESA_NOMBRE", properties.empresaNombre()),
                valor("BOOTSTRAP_EMPRESA_NIT", properties.empresaNit()),
                valor("BOOTSTRAP_ADMIN_DOCUMENTO", properties.documento()),
                valor("BOOTSTRAP_ADMIN_NOMBRES", properties.nombres()),
                valor("BOOTSTRAP_ADMIN_USERNAME", properties.username()),
                valor("BOOTSTRAP_ADMIN_EMAIL", properties.email()),
                valor("BOOTSTRAP_ADMIN_PASSWORD", properties.password())
        ).filter(value -> value != null).toList();

        if (!camposFaltantes.isEmpty()) {
            throw new IllegalStateException(
                    "Faltan variables para el administrador inicial: "
                            + String.join(", ", camposFaltantes)
            );
        }
        if (properties.password().length() < 12) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD debe tener al menos 12 caracteres"
            );
        }
    }

    private String valor(String nombre, String valor) {
        return valor == null || valor.isBlank() ? nombre : null;
    }
}
