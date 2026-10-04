package com.inmobiliaria.inmobiliaria_api.service.gerencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaRegistroService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditoriaRegistroService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void registrar(ContextoUsuario contexto, Long proyectoId, String modulo,
                          String accion, String entidadTipo, Long entidadId,
                          String descripcion, Object anterior, Object nuevo) {
        jdbcTemplate.update("""
                INSERT INTO auditoria_eventos
                (empresa_id, proyecto_id, actor_id, modulo, accion, entidad_tipo,
                 entidad_id, descripcion, datos_anteriores_json, datos_nuevos_json,
                 correlation_id, ocurrido_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                """, contexto.empresaId(), proyectoId, contexto.usuarioId(), modulo,
                accion, entidadTipo, entidadId, descripcion, json(anterior), json(nuevo),
                UUID.randomUUID().toString(), LocalDateTime.now(BOGOTA));
    }

    private String json(Object valor) {
        if (valor == null) return null;
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"no_serializable\"}";
        }
    }
}
