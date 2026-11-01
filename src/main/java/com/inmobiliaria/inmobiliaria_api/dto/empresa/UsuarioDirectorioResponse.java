package com.inmobiliaria.inmobiliaria_api.dto.empresa;

import java.util.List;

/**
 * Persona del equipo de la empresa, para mostrar nombres en lugar de ids (asesor de una meta,
 * autor de un evento de auditoría, responsable de una alerta). No lleva correo ni documento.
 */
public record UsuarioDirectorioResponse(Long id, String nombre, String nombreUsuario,
                                        String estado, List<String> roles) { }
