package com.inmobiliaria.inmobiliaria_api.service.sincronizacion.lectura;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import org.bson.types.Decimal128;

/**
 * Lee campos de un documento de MongoDB ya convertido a {@code Map} y los entrega en los tipos que
 * espera JDBC. Los campos obligatorios ausentes fallan con un mensaje que nombra el campo, para que
 * el error del proyector diga qué documento está incompleto.
 */
final class LectorDocumento {

    private final String coleccion;
    private final Object id;
    private final Map<String, Object> datos;

    LectorDocumento(String coleccion, Object id, Map<String, Object> datos) {
        this.coleccion = coleccion;
        this.id = id;
        this.datos = datos;
    }

    Long largo(String campo) {
        Object valor = datos.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        throw tipoInvalido(campo, valor, "un número entero");
    }

    Long largoObligatorio(String campo) {
        return obligatorio(campo, largo(campo));
    }

    String texto(String campo) {
        Object valor = datos.get(campo);
        return valor == null ? null : valor.toString();
    }

    String textoObligatorio(String campo) {
        return obligatorio(campo, texto(campo));
    }

    Timestamp fecha(String campo) {
        Object valor = datos.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Date fecha) {
            return new Timestamp(fecha.getTime());
        }
        if (valor instanceof Instant instante) {
            return Timestamp.from(instante);
        }
        if (valor instanceof LocalDateTime fechaLocal) {
            return Timestamp.valueOf(fechaLocal);
        }
        throw tipoInvalido(campo, valor, "una fecha");
    }

    Timestamp fechaObligatoria(String campo) {
        return obligatorio(campo, fecha(campo));
    }

    BigDecimal decimal(String campo) {
        Object valor = datos.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }
        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }
        if (valor instanceof Long || valor instanceof Integer) {
            return BigDecimal.valueOf(((Number) valor).longValue());
        }
        if (valor instanceof Number numero) {
            return BigDecimal.valueOf(numero.doubleValue());
        }
        throw tipoInvalido(campo, valor, "un número decimal");
    }

    BigDecimal decimalObligatorio(String campo) {
        return obligatorio(campo, decimal(campo));
    }

    /** Subdocumento embebido, o {@code null} si no existe. */
    @SuppressWarnings("unchecked")
    LectorDocumento subdocumento(String campo) {
        Object valor = datos.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Map<?, ?> mapa) {
            return new LectorDocumento(coleccion + "." + campo, id, (Map<String, Object>) mapa);
        }
        throw tipoInvalido(campo, valor, "un subdocumento");
    }

    private <T> T obligatorio(String campo, T valor) {
        if (valor == null) {
            throw new IllegalStateException("El documento " + id + " de " + coleccion
                    + " no tiene el campo obligatorio " + campo);
        }
        return valor;
    }

    private IllegalStateException tipoInvalido(String campo, Object valor, String esperado) {
        return new IllegalStateException("El campo " + campo + " del documento " + id + " de "
                + coleccion + " debía ser " + esperado + " y llegó como "
                + valor.getClass().getSimpleName());
    }
}
