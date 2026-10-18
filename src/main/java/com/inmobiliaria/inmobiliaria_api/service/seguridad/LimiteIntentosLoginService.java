package com.inmobiliaria.inmobiliaria_api.service.seguridad;

import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.DemasiadosIntentosException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Limita los intentos fallidos de inicio de sesión por dirección IP.
 * El contador vive en Redis y expira solo; si Redis no responde,
 * el login sigue funcionando y se conserva el bloqueo por cuenta en MySQL.
 */
@Service
public class LimiteIntentosLoginService {

    private static final Logger log = LoggerFactory.getLogger(LimiteIntentosLoginService.class);
    private static final int MAX_FALLOS_POR_IP = 20;
    private static final Duration VENTANA = Duration.ofMinutes(15);
    private static final String PREFIJO = "nexora:login:fallos:";

    private final StringRedisTemplate redis;

    public LimiteIntentosLoginService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void verificar(String ip) {
        try {
            String valor = redis.opsForValue().get(PREFIJO + ip);
            if (valor != null && Long.parseLong(valor) >= MAX_FALLOS_POR_IP) {
                throw new DemasiadosIntentosException(
                        "Demasiados intentos fallidos desde esta red. Intenta de nuevo en unos minutos."
                );
            }
        } catch (DataAccessException exception) {
            log.warn("Redis no disponible: se omite el límite de intentos por IP", exception);
        }
    }

    public void registrarFallo(String ip) {
        try {
            String clave = PREFIJO + ip;
            Long total = redis.opsForValue().increment(clave);
            if (total != null && total == 1L) {
                redis.expire(clave, VENTANA);
            }
        } catch (DataAccessException exception) {
            log.warn("Redis no disponible: no se registró el intento fallido", exception);
        }
    }
}