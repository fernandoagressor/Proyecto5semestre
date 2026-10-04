package com.inmobiliaria.inmobiliaria_api.service.empresa;

import com.inmobiliaria.inmobiliaria_api.dto.empresa.EmpresaResponse;
import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import com.inmobiliaria.inmobiliaria_api.repository.empresa.EmpresaRepository;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final ModuloEmpresaService moduloEmpresaService;

    public EmpresaService(
            EmpresaRepository empresaRepository,
            ModuloEmpresaService moduloEmpresaService
    ) {
        this.empresaRepository = empresaRepository;
        this.moduloEmpresaService = moduloEmpresaService;
    }

    @Transactional(readOnly = true)
    public EmpresaResponse obtenerActual(Long empresaId) {
        if (empresaId == null) {
            throw new RecursoNoEncontradoException(
                    "El usuario autenticado no tiene una empresa asignada"
            );
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La empresa asignada al usuario no existe"
                ));
        return EmpresaResponse.from(empresa, moduloEmpresaService.activos(empresaId));
    }
}
