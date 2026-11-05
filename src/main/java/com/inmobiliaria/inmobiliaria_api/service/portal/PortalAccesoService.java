package com.inmobiliaria.inmobiliaria_api.service.portal;

import com.inmobiliaria.inmobiliaria_api.dto.administracion.AdministracionDtos;
import com.inmobiliaria.inmobiliaria_api.dto.portal.PortalDtos;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.Usuario;
import com.inmobiliaria.inmobiliaria_api.repository.cliente.ClienteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.identidad.PersonaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.administracion.AdministracionUsuarioService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.gerencia.AuditoriaRegistroService;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Habilita el portal a un cliente: crea su usuario (tipo CLIENTE, rol CLIENTE, alcance propio)
 * con contraseña temporal y lo enlaza a la ficha del cliente. Las cuentas de clientes no cuentan
 * contra el límite de usuarios internos del plan.
 */
@Service
public class PortalAccesoService {

    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String SIMBOLOS = "!@#$%&*?";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClienteRepository clienteRepository;
    private final PersonaRepository personaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdministracionUsuarioService usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final AuditoriaRegistroService auditoria;

    public PortalAccesoService(ClienteRepository clienteRepository, PersonaRepository personaRepository,
                               UsuarioRepository usuarioRepository, AdministracionUsuarioService usuarios,
                               PasswordEncoder passwordEncoder, JdbcTemplate jdbc,
                               AuditoriaRegistroService auditoria) {
        this.clienteRepository = clienteRepository;
        this.personaRepository = personaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Transactional
    public PortalDtos.Acceso habilitar(ContextoUsuario ctx, Long clienteId) {
        Cliente cliente = clienteRepository.findForUpdate(clienteId, ctx.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe"));
        if (!"ACTIVO".equals(cliente.getEstado())) {
            throw new ReglaNegocioException("Solo los clientes activos pueden tener acceso al portal");
        }
        if (cliente.getUsuarioId() != null) {
            throw new ConflictoException("El cliente ya tiene acceso al portal; use reenviar acceso");
        }
        Persona persona = personaRepository.findById(cliente.getPersonaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La persona del cliente no existe"));
        // Usuario: el correo del cliente; si no tiene o ya lo usa otra cuenta, su número de documento.
        String correo = persona.getCorreo() == null ? "" : persona.getCorreo().trim().toLowerCase(Locale.ROOT);
        String documento = persona.getNumeroDocumento() == null ? "" : persona.getNumeroDocumento().trim();
        String nombreUsuario;
        String correoCuenta;
        if (!correo.isBlank() && !ocupado(correo)) {
            nombreUsuario = correo;
            correoCuenta = correo;
        } else if (!documento.isBlank() && !ocupado(documento)) {
            nombreUsuario = documento;
            correoCuenta = null;
        } else {
            throw new ConflictoException("Ya existe una cuenta con el correo o el documento de este cliente");
        }
        Long rolCliente = jdbc.queryForObject(
                "SELECT id FROM roles WHERE codigo = 'CLIENTE' AND empresa_id IS NULL AND activo = 1",
                Long.class);

        String password = passwordTemporal();
        Usuario usuario = usuarioRepository.saveAndFlush(new Usuario(
                ctx.empresaId(), persona.getId(), nombreUsuario, correoCuenta, passwordEncoder.encode(password)));
        jdbc.update("UPDATE usuarios SET tipo_usuario = 'CLIENTE' WHERE id = ?", usuario.getId());
        jdbc.update("""
                INSERT INTO usuario_roles (usuario_id, rol_id, principal, asignado_por, creado_en, actualizado_en)
                VALUES (?, ?, 1, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, usuario.getId(), rolCliente, ctx.usuarioId());
        jdbc.update("""
                INSERT INTO usuario_alcances (usuario_id, empresa_id, proyecto_id, tipo_alcance, asignado_por,
                                              vigente_desde, creado_en, actualizado_en)
                VALUES (?, ?, NULL, 'PROPIOS', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, usuario.getId(), ctx.empresaId(), ctx.usuarioId());
        int enlazados = jdbc.update(
                "UPDATE clientes SET usuario_id = ? WHERE id = ? AND empresa_id = ? AND usuario_id IS NULL",
                usuario.getId(), clienteId, ctx.empresaId());
        if (enlazados != 1) {
            throw new ConflictoException("El cliente cambió mientras se habilitaba el portal; intente de nuevo");
        }
        auditoria.registrar(ctx, null, "CLIENTES", "PORTAL_HABILITADO", "CLIENTE", clienteId,
                "Acceso al portal creado para " + nombreUsuario, null, "usuario " + usuario.getId());
        return new PortalDtos.Acceso(clienteId, usuario.getId(), nombreUsuario, correoCuenta, password);
    }

    /**
     * Al reservar: crea el acceso al portal si el cliente todavía no lo tiene. Devuelve null si ya
     * tenía acceso o si no se puede crear (cliente inactivo, correo y documento ya usados por otra
     * cuenta); en ese caso la reserva sigue normal y el acceso se habilita a mano en Ventas.
     */
    @Transactional
    public PortalDtos.Acceso habilitarSiFalta(ContextoUsuario ctx, Long clienteId) {
        Cliente cliente = clienteRepository.findByIdAndEmpresaId(clienteId, ctx.empresaId()).orElse(null);
        if (cliente == null || cliente.getUsuarioId() != null || !"ACTIVO".equals(cliente.getEstado())) {
            return null;
        }
        Persona persona = personaRepository.findById(cliente.getPersonaId()).orElse(null);
        if (persona == null) {
            return null;
        }
        String correo = persona.getCorreo() == null ? "" : persona.getCorreo().trim().toLowerCase(Locale.ROOT);
        String documento = persona.getNumeroDocumento() == null ? "" : persona.getNumeroDocumento().trim();
        boolean hayUsuarioLibre = (!correo.isBlank() && !ocupado(correo))
                || (!documento.isBlank() && !ocupado(documento));
        return hayUsuarioLibre ? habilitar(ctx, clienteId) : null;
    }

    private boolean ocupado(String identificador) {
        String valor = identificador.toLowerCase(Locale.ROOT);
        Long ocupados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE LOWER(nombre_usuario) = ? OR LOWER(correo) = ?",
                Long.class, valor, valor);
        return ocupados != null && ocupados > 0;
    }

    @Transactional
    public PortalDtos.Acceso reenviar(ContextoUsuario ctx, Long clienteId) {
        Cliente cliente = clienteRepository.findByIdAndEmpresaId(clienteId, ctx.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe"));
        if (cliente.getUsuarioId() == null) {
            throw new ReglaNegocioException("El cliente aún no tiene acceso al portal");
        }
        String password = passwordTemporal();
        usuarios.reiniciarCredenciales(ctx.usuarioId(), ctx.empresaId(), cliente.getUsuarioId(),
                new AdministracionDtos.ReinicioCredencialesRequest(password, "Reenvío de acceso al portal del cliente"));
        var cuenta = jdbc.queryForMap("SELECT nombre_usuario, correo FROM usuarios WHERE id = ?", cliente.getUsuarioId());
        return new PortalDtos.Acceso(clienteId, cliente.getUsuarioId(), (String) cuenta.get("nombre_usuario"),
                (String) cuenta.get("correo"), password);
    }

    static String passwordTemporal() {
        List<Character> chars = new ArrayList<>();
        chars.add(MAYUSCULAS.charAt(RANDOM.nextInt(MAYUSCULAS.length())));
        chars.add(MINUSCULAS.charAt(RANDOM.nextInt(MINUSCULAS.length())));
        chars.add(NUMEROS.charAt(RANDOM.nextInt(NUMEROS.length())));
        chars.add(SIMBOLOS.charAt(RANDOM.nextInt(SIMBOLOS.length())));
        String todos = MAYUSCULAS + MINUSCULAS + NUMEROS + SIMBOLOS;
        while (chars.size() < 20) {
            chars.add(todos.charAt(RANDOM.nextInt(todos.length())));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }
}
