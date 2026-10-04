package com.inmobiliaria.inmobiliaria_api.service.servicio;

import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.seguridad.EstadoUsuario;
import com.inmobiliaria.inmobiliaria_api.repository.cliente.ClienteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.crm.CrmAlcanceService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioClienteAlcanceService {
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CrmAlcanceService crmAlcanceService;
    private final JdbcTemplate jdbcTemplate;

    public ServicioClienteAlcanceService(ClienteRepository clienteRepository,
                                         UsuarioRepository usuarioRepository,
                                         CrmAlcanceService crmAlcanceService,
                                         JdbcTemplate jdbcTemplate) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.crmAlcanceService = crmAlcanceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<Cliente> clienteAutenticado(ContextoUsuario contexto) {
        return clienteRepository.findByUsuarioIdAndEmpresaIdAndEstado(
                contexto.usuarioId(), contexto.empresaId(), "ACTIVO");
    }

    @Transactional(readOnly = true)
    public Cliente validarCliente(ContextoUsuario contexto, Long solicitado) {
        Optional<Cliente> autenticado = clienteAutenticado(contexto);
        Long clienteId = autenticado.map(Cliente::getId).orElse(solicitado);
        if (clienteId == null) {
            throw new ReglaNegocioException("Debe indicar el cliente");
        }
        if (autenticado.isPresent() && !autenticado.get().getId().equals(clienteId)) {
            throw new AccesoDenegadoException("El cliente solo puede consultar su propia información");
        }
        return clienteRepository.findByIdAndEmpresaIdAndEstado(clienteId, contexto.empresaId(), "ACTIVO")
                .orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe o está inactivo"));
    }

    @Transactional(readOnly = true)
    public void validarRelacion(ContextoUsuario contexto, Long clienteId,
                                Long proyectoId, Long contratoId) {
        Cliente cliente = validarCliente(contexto, clienteId);
        if (contratoId != null) {
            Integer contratos = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM contratos c
                    LEFT JOIN contrato_clientes cc ON cc.contrato_id = c.id
                    WHERE c.id = ? AND c.empresa_id = ?
                      AND (c.cliente_principal_id = ? OR cc.cliente_id = ?)
                    """, Integer.class, contratoId, contexto.empresaId(), cliente.getId(), cliente.getId());
            if (contratos == null || contratos == 0) {
                throw new ReglaNegocioException("El contrato no pertenece al cliente indicado");
            }
        }
        if (proyectoId != null) {
            if (clienteAutenticado(contexto).isPresent()) {
                Integer vinculaciones = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM contratos c
                        LEFT JOIN contrato_clientes cc ON cc.contrato_id = c.id
                        WHERE c.empresa_id = ? AND c.proyecto_id = ?
                          AND (c.cliente_principal_id = ? OR cc.cliente_id = ?)
                        """, Integer.class, contexto.empresaId(), proyectoId, cliente.getId(), cliente.getId());
                if (vinculaciones == null || vinculaciones == 0) {
                    throw new AccesoDenegadoException("El cliente no está vinculado con el proyecto");
                }
            } else {
                crmAlcanceService.verificarRegistro(contexto, proyectoId, contexto.usuarioId());
            }
        } else if (clienteAutenticado(contexto).isEmpty()
                && !crmAlcanceService.obtener(contexto).empresaCompleta()) {
            throw new AccesoDenegadoException(
                    "Los registros sin proyecto requieren alcance completo sobre la empresa");
        }
    }

    @Transactional(readOnly = true)
    public AlcanceProyectos alcanceInterno(ContextoUsuario contexto) {
        return crmAlcanceService.obtener(contexto);
    }

    @Transactional(readOnly = true)
    public void validarUsuarioEmpresa(ContextoUsuario contexto, Long usuarioId) {
        if (usuarioId == null || !usuarioRepository.existsByIdAndEmpresaIdAndEstado(
                usuarioId, contexto.empresaId(), EstadoUsuario.ACTIVO)) {
            throw new ReglaNegocioException("El responsable debe ser un usuario activo de la empresa");
        }
    }
}
