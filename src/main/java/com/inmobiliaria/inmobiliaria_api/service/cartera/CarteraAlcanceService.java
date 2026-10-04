package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.entity.ventas.contrato.Contrato;
import com.inmobiliaria.inmobiliaria_api.repository.ventas.contrato.ContratoRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.crm.CrmAlcanceService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarteraAlcanceService {
    private final ContratoRepository contratoRepository;
    private final CrmAlcanceService crmAlcanceService;

    public CarteraAlcanceService(ContratoRepository contratoRepository, CrmAlcanceService crmAlcanceService) {
        this.contratoRepository = contratoRepository;
        this.crmAlcanceService = crmAlcanceService;
    }

    @Transactional(readOnly = true)
    public Contrato contrato(ContextoUsuario contexto, Long contratoId) {
        Contrato contrato = contratoRepository.findByIdAndEmpresaId(contratoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El contrato no existe"));
        crmAlcanceService.verificarRegistro(contexto, contrato.getProyectoId(), contrato.getAsesorId());
        return contrato;
    }

    @Transactional
    public Contrato contratoParaActualizar(ContextoUsuario contexto, Long contratoId) {
        Contrato contrato = contratoRepository.findForUpdate(contratoId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El contrato no existe"));
        crmAlcanceService.verificarRegistro(contexto, contrato.getProyectoId(), contrato.getAsesorId());
        return contrato;
    }

    @Transactional(readOnly = true)
    public List<Contrato> contratosAutorizados(ContextoUsuario contexto) {
        AlcanceProyectos alcance = crmAlcanceService.obtener(contexto);
        Specification<Contrato> especificacion = (root, query, cb) -> {
            Predicate empresa = cb.equal(root.get("empresaId"), contexto.empresaId());
            if (alcance.empresaCompleta()) {
                return empresa;
            }
            List<Predicate> opciones = new ArrayList<>();
            if (!alcance.proyectos().isEmpty()) {
                opciones.add(root.get("proyectoId").in(alcance.proyectos()));
            }
            if (alcance.registrosPropios()) {
                opciones.add(cb.equal(root.get("asesorId"), contexto.usuarioId()));
            }
            if (opciones.isEmpty()) {
                return cb.disjunction();
            }
            return cb.and(empresa, cb.or(opciones.toArray(Predicate[]::new)));
        };
        return contratoRepository.findAll(especificacion);
    }
}
