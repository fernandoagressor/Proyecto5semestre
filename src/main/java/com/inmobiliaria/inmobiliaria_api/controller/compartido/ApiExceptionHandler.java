package com.inmobiliaria.inmobiliaria_api.controller.compartido;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.AccesoDenegadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.NoAutorizadoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.DemasiadosIntentosException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            AuthenticationException.class,
            NoAutorizadoException.class
    })
    ProblemDetail noAutorizado(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Credenciales o sesión inválidas"
        );
        problem.setTitle("No autorizado");
        problem.setType(URI.create("urn:inmobiliaria:error:no-autorizado"));
        return problem;
    }

    @ExceptionHandler({
            AccesoDenegadoException.class,
            AuthorizationDeniedException.class
    })
    ProblemDetail accesoDenegado(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "No tiene autorización para acceder a este recurso"
        );
        problem.setTitle("Acceso denegado");
        problem.setType(URI.create("urn:inmobiliaria:error:acceso-denegado"));
        return problem;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Recurso no encontrado");
        problem.setType(URI.create("urn:inmobiliaria:error:no-encontrado"));
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException exception) {
        Map<String, String> errores = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errores.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "La solicitud contiene campos inválidos"
        );
        problem.setTitle("Error de validación");
        problem.setType(URI.create("urn:inmobiliaria:error:validacion"));
        problem.setProperty("errores", errores);
        return problem;
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail reglaNegocio(ReglaNegocioException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage()
        );
        problem.setTitle("Regla de negocio no satisfecha");
        problem.setType(URI.create("urn:inmobiliaria:error:regla-negocio"));
        return problem;
    }

    @ExceptionHandler({
            ConflictoException.class,
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class
    })
    ProblemDetail conflicto(RuntimeException exception) {
        String detalle = exception instanceof ConflictoException
                ? exception.getMessage()
                : "La operación entra en conflicto con un registro existente";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                detalle
        );
        problem.setTitle("Conflicto");
        problem.setType(URI.create("urn:inmobiliaria:error:conflicto"));
        return problem;
    }
    @ExceptionHandler(DemasiadosIntentosException.class)
    ProblemDetail demasiadosIntentos(DemasiadosIntentosException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage()
        );
        problem.setTitle("Demasiados intentos");
        problem.setType(URI.create("urn:inmobiliaria:error:demasiados-intentos"));
        return problem;
    }
}
