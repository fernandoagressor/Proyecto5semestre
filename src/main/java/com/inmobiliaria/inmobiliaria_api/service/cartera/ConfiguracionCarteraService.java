package com.inmobiliaria.inmobiliaria_api.service.cartera;

import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConfiguracionCarteraRequest;
import com.inmobiliaria.inmobiliaria_api.dto.cartera.ConfiguracionCarteraResponse;
import com.inmobiliaria.inmobiliaria_api.entity.cartera.ConfiguracionCartera;
import com.inmobiliaria.inmobiliaria_api.repository.cartera.ConfiguracionCarteraRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracionCarteraService {
    private final ConfiguracionCarteraRepository repository;

    public ConfiguracionCarteraService(ConfiguracionCarteraRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ConfiguracionCartera obtenerEntidad(Long empresaId) {
        return repository.findByEmpresaId(empresaId)
                .orElseGet(() -> repository.save(new ConfiguracionCartera(empresaId)));
    }

    @Transactional
    public ConfiguracionCarteraResponse obtener(ContextoUsuario contexto) {
        return ConfiguracionCarteraResponse.from(obtenerEntidad(contexto.empresaId()));
    }

    @Transactional
    public ConfiguracionCarteraResponse actualizar(
            ContextoUsuario contexto,
            ConfiguracionCarteraRequest request
    ) {
        ConfiguracionCartera configuracion = obtenerEntidad(contexto.empresaId());
        configuracion.actualizar(
                request.tasaEfectivaAnual(),
                request.tasaMoraEfectivaAnual(),
                request.periodicidad(),
                request.diasPrimeraCuota(),
                request.autoGenerarPlan()
        );
        return ConfiguracionCarteraResponse.from(configuracion);
    }
}
