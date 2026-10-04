package com.inmobiliaria.inmobiliaria_api.dto.cliente;

import com.inmobiliaria.inmobiliaria_api.entity.cliente.Cliente;
import com.inmobiliaria.inmobiliaria_api.entity.identidad.Persona;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClienteResponse(
        Long id,
        Long empresaId,
        Long personaId,
        Long usuarioId,
        String codigoCliente,
        String tipoPersona,
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        String nombreCompleto,
        String telefono,
        String correo,
        String direccion,
        String origen,
        String observaciones,
        String estado,
        LocalDate fechaAlta,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static ClienteResponse from(Cliente cliente, Persona persona) {
        String nombre = persona.getNombres();
        if (persona.getApellidos() != null && !persona.getApellidos().isBlank()) {
            nombre += " " + persona.getApellidos();
        }
        return new ClienteResponse(
                cliente.getId(),
                cliente.getEmpresaId(),
                cliente.getPersonaId(),
                cliente.getUsuarioId(),
                cliente.getCodigoCliente(),
                persona.getTipoPersona(),
                persona.getTipoDocumento(),
                persona.getNumeroDocumento(),
                persona.getNombres(),
                persona.getApellidos(),
                nombre,
                persona.getTelefono(),
                persona.getCorreo(),
                persona.getDireccion(),
                cliente.getOrigen(),
                cliente.getObservaciones(),
                cliente.getEstado(),
                cliente.getFechaAlta(),
                cliente.getCreadoEn(),
                cliente.getActualizadoEn()
        );
    }
}
