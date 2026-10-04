package com.inmobiliaria.inmobiliaria_api.dto.administracion;

import com.inmobiliaria.inmobiliaria_api.dto.empresa.EmpresaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdministracionDtos {

    private AdministracionDtos() {
    }

    public record ResumenGlobalResponse(
            long empresas,
            long empresasActivas,
            long empresasSuspendidas,
            long empresasInactivas,
            long proyectos,
            long usuarios,
            long usuariosActivos
    ) {
    }

    public record SerieAnaliticaGlobalResponse(
            String categoria,
            BigDecimal valor,
            long cantidad
    ) {
    }

    public record EmpresaAnaliticaGlobalResponse(
            Long empresaId,
            String codigo,
            String nombre,
            String estado,
            long proyectos,
            long usuarios,
            long lotes,
            long reservasActivas,
            long contratosVigentes,
            BigDecimal ventas,
            BigDecimal recaudo,
            BigDecimal cartera,
            BigDecimal carteraVencida
    ) {
    }

    public record AnaliticaGlobalResponse(
            BigDecimal ventas,
            BigDecimal recaudo,
            BigDecimal cartera,
            BigDecimal carteraVencida,
            BigDecimal inventarioDisponible,
            long lotes,
            long lotesDisponibles,
            long lotesReservados,
            long lotesVendidos,
            long lotesBloqueados,
            long reservasActivas,
            long contratosVigentes,
            List<EmpresaAnaliticaGlobalResponse> empresas,
            List<SerieAnaliticaGlobalResponse> lotesPorEstado,
            List<SerieAnaliticaGlobalResponse> proyectosPorEstado,
            List<SerieAnaliticaGlobalResponse> ventasMensuales,
            List<SerieAnaliticaGlobalResponse> recaudoMensual
    ) {
    }

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
            long totalProyectos,
            long totalUsuarios,
            Long version,
            LocalDateTime creadoEn,
            LocalDateTime actualizadoEn
    ) {
    }

    public record EmpresaCrearRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 160) String nombre,
            @NotBlank @Size(max = 30) String nit,
            @Size(max = 240) String direccion,
            @Size(max = 100) String ciudad,
            @Size(max = 100) String departamento,
            @Size(max = 40) String telefono,
            @Email @Size(max = 180) String correo,
            @Size(max = 500) String logoUrl,
            @NotBlank @Size(max = 60) String zonaHoraria,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String moneda,
            @NotNull @Min(1) @Max(10000) Integer limiteProyectos,
            @NotNull @Min(1) @Max(100000) Integer limiteUsuarios,
            @Size(max = 20) List<String> modulos,
            @NotNull @Valid AdministradorInicialRequest administrador
    ) {
    }

    public record EmpresaActualizarRequest(
            @NotBlank @Size(max = 30) String codigo,
            @NotBlank @Size(max = 160) String nombre,
            @NotBlank @Size(max = 30) String nit,
            @Size(max = 240) String direccion,
            @Size(max = 100) String ciudad,
            @Size(max = 100) String departamento,
            @Size(max = 40) String telefono,
            @Email @Size(max = 180) String correo,
            @Size(max = 500) String logoUrl,
            @NotBlank @Size(max = 60) String zonaHoraria,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String moneda,
            @NotNull @Min(1) @Max(10000) Integer limiteProyectos,
            @NotNull @Min(1) @Max(100000) Integer limiteUsuarios,
            @Size(max = 20) List<String> modulos,
            @NotNull @Min(0) Long version
    ) {
    }

    public record AdministradorInicialRequest(
            @NotBlank @Size(max = 20) String tipoDocumento,
            @NotBlank @Size(max = 40) String numeroDocumento,
            @NotBlank @Size(max = 120) String nombres,
            @Size(max = 120) String apellidos,
            @NotBlank @Size(min = 3, max = 80) String nombreUsuario,
            @NotBlank @Email @Size(max = 180) String correo,
            @NotBlank
            @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S+$")
            String passwordTemporal
    ) {
    }

    public record CambioEstadoEmpresaRequest(
            @NotBlank @Pattern(regexp = "ACTIVA|SUSPENDIDA|INACTIVA") String estado,
            @NotBlank @Size(max = 300) String motivo,
            @NotNull @Min(0) Long version
    ) {
    }

    public record UsuarioResponse(
            Long id,
            Long empresaId,
            Long personaId,
            String nombreCompleto,
            String nombres,
            String apellidos,
            String tipoDocumento,
            String numeroDocumento,
            String nombreUsuario,
            String correo,
            String telefono,
            String estado,
            boolean requiereCambioPassword,
            boolean mfaHabilitado,
            LocalDateTime ultimoAccesoEn,
            List<Long> rolIds,
            List<String> roles,
            List<AlcanceResponse> alcances,
            Long version
    ) {
    }

    public record UsuarioCrearRequest(
            @NotBlank @Size(max = 20) String tipoDocumento,
            @NotBlank @Size(max = 40) String numeroDocumento,
            @NotBlank @Size(max = 120) String nombres,
            @Size(max = 120) String apellidos,
            @Size(max = 40) String telefono,
            @NotBlank @Size(min = 3, max = 80) String nombreUsuario,
            @NotBlank @Email @Size(max = 180) String correo,
            @NotBlank
            @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S+$")
            String passwordTemporal,
            @NotEmpty List<@NotNull Long> rolIds,
            @NotEmpty List<@Valid AlcanceRequest> alcances
    ) {
    }

    public record UsuarioActualizarRequest(
            @NotBlank @Size(max = 120) String nombres,
            @Size(max = 120) String apellidos,
            @Size(max = 40) String telefono,
            @NotBlank @Email @Size(max = 180) String correo,
            @NotEmpty List<@NotNull Long> rolIds,
            @NotEmpty List<@Valid AlcanceRequest> alcances,
            @NotNull @Min(0) Long version
    ) {
    }

    public record AlcanceRequest(
            @NotBlank @Pattern(regexp = "EMPRESA|PROYECTO|PROPIOS") String tipo,
            Long proyectoId
    ) {
    }

    public record AlcanceResponse(
            Long id,
            String tipo,
            Long proyectoId,
            String proyectoNombre
    ) {
    }

    public record CambioEstadoUsuarioRequest(
            @NotBlank @Pattern(regexp = "ACTIVO|INACTIVO|BLOQUEADO") String estado,
            @NotBlank @Size(max = 300) String motivo,
            @NotNull @Min(0) Long version
    ) {
    }

    public record ReinicioCredencialesRequest(
            @NotBlank
            @Size(min = 12, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S+$")
            String passwordTemporal,
            @NotBlank @Size(max = 300) String motivo
    ) {
    }

    public record RolResponse(
            Long id,
            Long empresaId,
            String codigo,
            String nombre,
            String descripcion,
            boolean sistema,
            boolean protegido,
            boolean activo,
            List<String> permisos
    ) {
    }

    public record RolGuardarRequest(
            @NotBlank @Size(max = 60) String codigo,
            @NotBlank @Size(max = 100) String nombre,
            @Size(max = 500) String descripcion,
            boolean activo,
            List<@NotNull Long> permisoIds
    ) {
    }

    public record PermisoResponse(
            Long id,
            String codigo,
            String modulo,
            String nombre,
            String descripcion,
            boolean sensible
    ) {
    }
}
