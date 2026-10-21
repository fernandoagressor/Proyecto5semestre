package com.inmobiliaria.inmobiliaria_api.security;

import com.inmobiliaria.inmobiliaria_api.config.RedisCacheConfig;
import com.inmobiliaria.inmobiliaria_api.repository.seguridad.UsuarioRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Optional;

/**
 * Caché de la versión vigente de credenciales por usuario.
 * Evita consultar MySQL en cada petición autenticada.
 */
@Component
public class TokenVersionCache {
    private final UsuarioRepository usuarioRepository;

    public TokenVersionCache(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }
    @Cacheable(cacheNames = RedisCacheConfig.TOKEN_VERSION, key = "#usuarioId",
                unless = "#result == null")
    public Optional<Long> obtener(Long usuarioId) {
        return usuarioRepository.findTokenVersionById(usuarioId);
    }
    @CacheEvict(cacheNames = RedisCacheConfig.TOKEN_VERSION, key = "#usuarioId")
    public void invalidar(Long usuarioId) {}
}
