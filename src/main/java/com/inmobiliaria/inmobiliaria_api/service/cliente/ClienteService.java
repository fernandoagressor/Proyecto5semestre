package com.inmobiliaria.inmobiliaria_api.service.cliente;

import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cliente.ClienteResponse;
import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import com.inmobiliaria.inmobiliaria_api.repository.cliente.ClienteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.identidad.PersonaRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PersonaRepository personaRepository;

    public ClienteService(
            ClienteRepository clienteRepository,
            PersonaRepository personaRepository
    ) {
        this.clienteRepository = clienteRepository;
        this.personaRepository = personaRepository;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> listar(
            ContextoUsuario contexto,
            String texto,
            String estado,
            int pagina,
            int tamano
    ) {
        Specification<Cliente> filtro = (root, query, cb) ->
                cb.equal(root.get("empresaId"), contexto.empresaId());
        if (estado != null && !estado.isBlank()) {
            String normalizado = estado.trim().toUpperCase(Locale.ROOT);
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("estado"), normalizado));
        }
        if (texto != null && !texto.isBlank()) {
            String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
            filtro = filtro.and((root, query, cb) -> {
                Subquery<Long> personas = query.subquery(Long.class);
                Root<Persona> persona = personas.from(Persona.class);
                personas.select(persona.get("id")).where(cb.or(
                        cb.like(cb.lower(persona.get("nombres")), patron),
                        cb.like(cb.lower(persona.get("apellidos")), patron),
                        cb.like(cb.lower(persona.get("numeroDocumento")), patron),
                        cb.like(cb.lower(persona.get("correo")), patron),
                        cb.like(cb.lower(persona.get("telefono")), patron)
                ));
                Predicate codigo = cb.like(
                        cb.lower(root.get("codigoCliente")), patron);
                return cb.or(codigo, root.get("personaId").in(personas));
            });
        }
        Page<Cliente> resultado = clienteRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn")
                )
        );
        Map<Long, Persona> personas = new HashMap<>();
        personaRepository.findAllById(
                resultado.getContent().stream().map(Cliente::getPersonaId).toList()
        ).forEach(persona -> personas.put(persona.getId(), persona));
        return PaginaResponse.from(resultado, cliente -> ClienteResponse.from(
                cliente,
                personas.get(cliente.getPersonaId())
        ));
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtener(ContextoUsuario contexto, Long clienteId) {
        Cliente cliente = clienteRepository.findByIdAndEmpresaId(
                clienteId,
                contexto.empresaId()
        ).orElseThrow(() -> new RecursoNoEncontradoException(
                "El cliente no existe"
        ));
        return respuesta(cliente);
    }

    @Transactional
    public ClienteResponse crear(ContextoUsuario contexto, ClienteRequest request) {
        String tipoDocumento = request.tipoDocumento().trim().toUpperCase(Locale.ROOT);
        String numeroDocumento = request.numeroDocumento().trim();
        Persona persona = personaRepository
                .findByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento)
                .orElseGet(() -> personaRepository.save(new Persona(
                        request.tipoPersona(),
                        tipoDocumento,
                        numeroDocumento,
                        request.nombres(),
                        request.apellidos(),
                        request.telefono(),
                        request.correo(),
                        request.direccion()
                )));
        if (clienteRepository.findByEmpresaIdAndPersonaId(
                contexto.empresaId(), persona.getId()).isPresent()) {
            throw new ConflictoException(
                    "La persona ya está registrada como cliente de la empresa"
            );
        }
        Cliente cliente = clienteRepository.save(new Cliente(
                contexto.empresaId(),
                persona.getId(),
                generarCodigo(),
                request.origen(),
                request.observaciones()
        ));
        return ClienteResponse.from(cliente, persona);
    }

    @Transactional
    public ClienteResponse actualizar(
            ContextoUsuario contexto,
            Long clienteId,
            ClienteRequest request
    ) {
        Cliente cliente = obtenerParaActualizar(contexto, clienteId);
        Persona persona = personaRepository.findById(cliente.getPersonaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La identidad del cliente no existe"
                ));
        String tipoDocumento = request.tipoDocumento().trim().toUpperCase(Locale.ROOT);
        String numeroDocumento = request.numeroDocumento().trim();
        personaRepository.findByTipoDocumentoAndNumeroDocumento(
                tipoDocumento, numeroDocumento
        ).filter(otra -> !otra.getId().equals(persona.getId())).ifPresent(otra -> {
            throw new ConflictoException(
                    "El documento ya pertenece a otra persona"
            );
        });
        persona.actualizar(
                request.tipoPersona(),
                tipoDocumento,
                numeroDocumento,
                request.nombres(),
                request.apellidos(),
                request.telefono(),
                request.correo(),
                request.direccion()
        );
        cliente.actualizar(request.origen(), request.observaciones());
        return ClienteResponse.from(cliente, persona);
    }

    @Transactional
    public ClienteResponse cambiarEstado(
            ContextoUsuario contexto,
            Long clienteId,
            String estado
    ) {
        Cliente cliente = obtenerParaActualizar(contexto, clienteId);
        cliente.cambiarEstado(estado);
        return respuesta(cliente);
    }

    private Cliente obtenerParaActualizar(
            ContextoUsuario contexto,
            Long clienteId
    ) {
        return clienteRepository.findForUpdate(clienteId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El cliente no existe"
                ));
    }

    private ClienteResponse respuesta(Cliente cliente) {
        Persona persona = personaRepository.findById(cliente.getPersonaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La identidad del cliente no existe"
                ));
        return ClienteResponse.from(cliente, persona);
    }

    private static String generarCodigo() {
        return "CLI-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }
}
