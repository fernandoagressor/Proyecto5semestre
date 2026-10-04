package com.inmobiliaria.inmobiliaria_api.entity.gerencia;

import com.inmobiliaria.inmobiliaria_api.entity.compartido.EntidadAuditable;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Entity
@Table(name = "reglas_comision")
public class ReglaComision extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;
    @Column(name = "proyecto_id")
    private Long proyectoId;
    @Column(nullable = false, length = 140)
    private String nombre;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoReglaComision tipo;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal valor;
    @Column(name = "condicion_causacion", nullable = false, length = 40)
    private String condicionCausacion;
    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;
    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;
    @Column(nullable = false)
    private boolean activo;
    @Version
    @Column(nullable = false)
    private Long version;

    protected ReglaComision() { }

    public ReglaComision(Long empresaId, Long proyectoId, String nombre,
                         TipoReglaComision tipo, BigDecimal valor,
                         String condicionCausacion, LocalDate vigenteDesde,
                         LocalDate vigenteHasta) {
        this.empresaId = empresaId;
        this.proyectoId = proyectoId;
        this.nombre = requerido(nombre, "El nombre de la regla es obligatorio");
        this.tipo = tipo;
        this.valor = normalizar(valor);
        this.condicionCausacion = requerido(condicionCausacion,
                "La condición de causación es obligatoria");
        this.vigenteDesde = vigenteDesde;
        this.vigenteHasta = vigenteHasta;
        this.activo = true;
        validar();
    }

    public void actualizar(String nombre, TipoReglaComision tipo, BigDecimal valor,
                           String condicionCausacion, LocalDate vigenteDesde,
                           LocalDate vigenteHasta) {
        if (!activo) {
            throw new ReglaNegocioException("Una regla cerrada no puede modificarse");
        }
        this.nombre = requerido(nombre, "El nombre de la regla es obligatorio");
        this.tipo = tipo;
        this.valor = normalizar(valor);
        this.condicionCausacion = requerido(condicionCausacion,
                "La condición de causación es obligatoria");
        this.vigenteDesde = vigenteDesde;
        this.vigenteHasta = vigenteHasta;
        validar();
    }

    public void cerrar(LocalDate fechaFin) {
        if (!activo) {
            throw new ReglaNegocioException("La regla ya está cerrada");
        }
        if (fechaFin == null || fechaFin.isBefore(vigenteDesde)) {
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la vigencia inicial");
        }
        vigenteHasta = fechaFin;
        activo = false;
    }

    public BigDecimal calcular(BigDecimal base) {
        if (base == null || base.signum() < 0) {
            throw new ReglaNegocioException("La base de comisión no puede ser negativa");
        }
        BigDecimal resultado = tipo == TipoReglaComision.PORCENTAJE
                ? base.multiply(valor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : valor;
        return resultado.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean vigente(LocalDate fecha) {
        return activo && !fecha.isBefore(vigenteDesde)
                && (vigenteHasta == null || !fecha.isAfter(vigenteHasta));
    }

    private void validar() {
        if (tipo == null || vigenteDesde == null) {
            throw new ReglaNegocioException("El tipo y la fecha inicial son obligatorios");
        }
        if (valor.signum() < 0) {
            throw new ReglaNegocioException("El valor de la comisión no puede ser negativo");
        }
        if (tipo == TipoReglaComision.PORCENTAJE
                && valor.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ReglaNegocioException("El porcentaje de comisión no puede superar 100");
        }
        if (vigenteHasta != null && vigenteHasta.isBefore(vigenteDesde)) {
            throw new ReglaNegocioException("La vigencia final no puede ser anterior a la inicial");
        }
    }

    private static BigDecimal normalizar(BigDecimal valor) {
        if (valor == null) {
            throw new ReglaNegocioException("El valor de la regla es obligatorio");
        }
        return valor.setScale(6, RoundingMode.HALF_UP);
    }

    private static String requerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new ReglaNegocioException(mensaje);
        return valor.trim();
    }

    public Long getId() { return id; }
    public Long getEmpresaId() { return empresaId; }
    public Long getProyectoId() { return proyectoId; }
    public String getNombre() { return nombre; }
    public TipoReglaComision getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public String getCondicionCausacion() { return condicionCausacion; }
    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public boolean isActivo() { return activo; }
    public Long getVersion() { return version; }
}
