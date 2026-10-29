package com.inmobiliaria.inmobiliaria_api.service.sincronizacion;

import com.inmobiliaria.inmobiliaria_api.config.RedisCacheConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borra de Redis los dashboards de una empresa cuando el Change Stream proyecta un cambio suyo,
 * para que no muestren datos de hasta 5 minutos atrás (el TTL de la caché).
 *
 * <ul>
 *   <li>Los cambios solo se anotan; cada {@code app.sync.invalidacion-dashboards} (2 s por defecto)
 *       se borran de una vez las llaves de las empresas anotadas. Así una ráfaga de miles de
 *       cambios (por ejemplo, un migrador) cuesta un borrado por empresa y no uno por cambio.</li>
 *   <li>Un cambio sin empresa (una eliminación) borra los dashboards de todas las empresas.</li>
 *   <li>Usa {@code SCAN}, no {@code KEYS}, para no bloquear Redis.</li>
 *   <li>Si Redis falla, se registra y la caché se corrige sola al vencer su TTL.</li>
 * </ul>
 *
 * <p>Llaves: {@code nexora:dashboards::<tablero>:<empresaId>:<usuarioId>...} (ver {@code DashboardService}).</p>
 */
@Component
public class InvalidadorCacheDashboards implements OyenteProyeccion {

    static final List<String> TABLEROS = List.of("gerencial", "comercial", "cartera", "servicio");
    private static final String PREFIJO = RedisCacheConfig.PREFIJO + RedisCacheConfig.DASHBOARDS + "::";
    private static final int LOTE = 500;
    private static final Logger log = LoggerFactory.getLogger(InvalidadorCacheDashboards.class);

    private final ObjectProvider<StringRedisTemplate> redis;
    private final Set<Long> pendientes = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean todas = new AtomicBoolean();

    public InvalidadorCacheDashboards(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis;
    }

    @Override
    public void proyectado(CambioDocumento cambio) {
        Object empresa = cambio.datos() == null ? null : cambio.datos().get("empresaId");
        if (empresa instanceof Number numero) {
            pendientes.add(numero.longValue());
        } else {
            todas.set(true);
        }
    }

    /** Anota que hay que borrar los dashboards de todas las empresas (p. ej. tras reconstruir los espejos). */
    public void marcarTodas() {
        todas.set(true);
    }

    /** Borra las llaves anotadas. @return cuántas llaves se borraron */
    @Scheduled(fixedDelayString = "${app.sync.invalidacion-dashboards:PT2S}",
            initialDelayString = "${app.sync.invalidacion-dashboards:PT2S}")
    public long vaciar() {
        StringRedisTemplate plantilla = redis.getIfAvailable();
        if (plantilla == null || (!todas.get() && pendientes.isEmpty())) {
            return 0;
        }
        List<String> patrones = new ArrayList<>();
        if (todas.getAndSet(false)) {
            pendientes.clear();
            patrones.add(PREFIJO + "*");
        } else {
            List<Long> empresas = List.copyOf(pendientes);
            pendientes.removeAll(empresas);
            for (Long empresa : empresas) {
                TABLEROS.forEach(tablero -> patrones.add(PREFIJO + tablero + ":" + empresa + ":*"));
            }
        }
        try {
            long borradas = 0;
            for (String patron : patrones) {
                borradas += borrar(plantilla, patron);
            }
            return borradas;
        } catch (RuntimeException e) {
            log.warn("No se pudo invalidar la caché de dashboards; se corregirá al vencer su TTL", e);
            return 0;
        }
    }

    private static long borrar(StringRedisTemplate plantilla, String patron) {
        Long borradas = plantilla.execute((RedisCallback<Long>) conexion -> {
            long total = 0;
            List<byte[]> lote = new ArrayList<>();
            ScanOptions opciones = ScanOptions.scanOptions().match(patron).count(LOTE).build();
            try (Cursor<byte[]> cursor = conexion.keyCommands().scan(opciones)) {
                while (cursor.hasNext()) {
                    lote.add(cursor.next());
                    if (lote.size() == LOTE) {
                        total += eliminar(conexion, lote);
                    }
                }
            }
            return total + eliminar(conexion, lote);
        });
        return borradas == null ? 0 : borradas;
    }

    private static long eliminar(RedisConnection conexion, List<byte[]> llaves) {
        if (llaves.isEmpty()) {
            return 0;
        }
        Long borradas = conexion.keyCommands().del(llaves.toArray(byte[][]::new));
        llaves.clear();
        return borradas == null ? 0 : borradas;
    }

    /** Llave de un tablero tal como la guarda Spring Cache (sin los sufijos de proyecto y fechas). */
    static String llave(String tablero, long empresa, long usuario) {
        return PREFIJO + tablero + ":" + empresa + ":" + usuario;
    }
}
