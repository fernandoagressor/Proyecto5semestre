package com.inmobiliaria.inmobiliaria_api.dto.empresa;

import com.inmobiliaria.inmobiliaria_api.entity.empresa.Empresa;
import java.util.List;

public record EmpresaResponse(
        Long id,
        String codigo,
        String nombre,
        String nit,
        String direccion,
        String ciudad,
        String departamento,
        String telefono,
        String correo,
        String logoUrl,
        String zonaHoraria,
        String moneda,
        String estado,
        Integer limiteProyectos,
        Integer limiteUsuarios,
        List<String> modulos,
        Long version
) {
    public static EmpresaResponse from(Empresa empresa, List<String> modulos) {
        return new EmpresaResponse(
                empresa.getId(),
                empresa.getCodigo(),
                empresa.getNombre(),
                empresa.getNit(),
                empresa.getDireccion(),
                empresa.getCiudad(),
                empresa.getDepartamento(),
                empresa.getTelefono(),
                empresa.getCorreo(),
                empresa.getLogoUrl(),
                empresa.getZonaHoraria(),
                empresa.getMoneda(),
                empresa.getEstado(),
                empresa.getLimiteProyectos(),
                empresa.getLimiteUsuarios(),
                modulos,
                empresa.getVersion()
        );
    }
}
