package com.inmobiliaria.inmobiliaria_api.service.simulacion;

import com.inmobiliaria.inmobiliaria_api.dto.compartido.PaginaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.simulacion.SimulacionCuotaResponse;
import com.inmobiliaria.inmobiliaria_api.dto.simulacion.SimulacionRequest;
import com.inmobiliaria.inmobiliaria_api.dto.simulacion.SimulacionResponse;
import com.inmobiliaria.inmobiliaria_api.entity.compartido.excepcion.ReglaNegocioException;
import com.inmobiliaria.inmobiliaria_api.entity.crm.EstadoOportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.crm.Oportunidad;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.EstadoLote;
import com.inmobiliaria.inmobiliaria_api.entity.inventario.Lote;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.EstadoSimulacion;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.Simulacion;
import com.inmobiliaria.inmobiliaria_api.entity.simulacion.SimulacionCuota;
import com.inmobiliaria.inmobiliaria_api.repository.inventario.LoteRepository;
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionCuotaRepository;
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.crm.CrmAlcanceService;
import com.inmobiliaria.inmobiliaria_api.service.crm.OportunidadService;
import com.inmobiliaria.inmobiliaria_api.service.inventario.AlcanceProyectos;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulacionService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final SimulacionRepository simulacionRepository;
    private final SimulacionCuotaRepository cuotaRepository;
    private final LoteRepository loteRepository;
    private final OportunidadService oportunidadService;
    private final CrmAlcanceService crmAlcanceService;
    private final PlanCuotasService planCuotasService;

    public SimulacionService(
            SimulacionRepository simulacionRepository,
            SimulacionCuotaRepository cuotaRepository,
            LoteRepository loteRepository,
            OportunidadService oportunidadService,
            CrmAlcanceService crmAlcanceService,
            PlanCuotasService planCuotasService
    ) {
        this.simulacionRepository = simulacionRepository;
        this.cuotaRepository = cuotaRepository;
        this.loteRepository = loteRepository;
        this.oportunidadService = oportunidadService;
        this.crmAlcanceService = crmAlcanceService;
        this.planCuotasService = planCuotasService;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<SimulacionResponse> listar(
            ContextoUsuario contexto,
            EstadoSimulacion estado,
            Long proyectoId,
            Long oportunidadId,
            int pagina,
            int tamano
    ) {
        Specification<Simulacion> filtro = filtroAlcance(contexto);
        if (estado != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("estado"), estado));
        }
        if (proyectoId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("proyectoId"), proyectoId));
        }
        if (oportunidadId != null) {
            filtro = filtro.and((root, query, cb) ->
                    cb.equal(root.get("oportunidadId"), oportunidadId));
        }
        Page<Simulacion> resultado = simulacionRepository.findAll(
                filtro,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn")
                )
        );
        return PaginaResponse.from(
                resultado,
                simulacion -> SimulacionResponse.from(simulacion, List.of())
        );
    }

    @Transactional(readOnly = true)
    public SimulacionResponse obtener(
            ContextoUsuario contexto,
            Long simulacionId
    ) {
        Simulacion simulacion = obtenerAutorizada(contexto, simulacionId);
        return respuestaCompleta(simulacion);
    }

    @Transactional
    public SimulacionResponse crear(
            ContextoUsuario contexto,
            SimulacionRequest request
    ) {
        Oportunidad oportunidad = oportunidadService.obtenerParaActualizar(
                contexto,
                request.oportunidadId()
        );
        if (oportunidad.getEstado() != EstadoOportunidad.ABIERTA) {
            throw new ReglaNegocioException(
                    "La oportunidad debe estar abierta"
            );
        }
        if (!oportunidad.tieneLote(request.loteId())) {
            throw new ReglaNegocioException(
                    "El lote debe estar vinculado a la oportunidad"
            );
        }
        Lote lote = loteRepository.findByIdAndEmpresaIdAndProyectoId(
                request.loteId(),
                contexto.empresaId(),
                oportunidad.getProyectoId()
        ).orElseThrow(() -> new ReglaNegocioException(
                "El lote no pertenece al proyecto de la oportunidad"
        ));
        if (lote.getEstado() != EstadoLote.DISPONIBLE) {
            throw new ReglaNegocioException(
                    "Solo se pueden simular lotes disponibles"
            );
        }

        validarFechas(request.fechaPrimeraCuota(), request.vigenteHasta());
        BigDecimal valorLote = dinero(lote.getValorLista());
        BigDecimal descuento = dinero(request.descuento());
        BigDecimal cuotaInicial = dinero(request.cuotaInicial());
        BigDecimal saldo = dinero(
                valorLote.subtract(descuento).subtract(cuotaInicial)
        );
        if (descuento.add(cuotaInicial).compareTo(valorLote) > 0) {
            throw new ReglaNegocioException(
                    "El descuento y la cuota inicial superan el valor del lote"
            );
        }

        Simulacion simulacion = new Simulacion(
                contexto.empresaId(),
                oportunidad.getProyectoId(),
                generarCodigo(),
                oportunidad.getId(),
                lote.getId(),
                contexto.usuarioId(),
                valorLote,
                descuento,
                cuotaInicial,
                saldo,
                request.numeroCuotas(),
                request.tasaEfectivaAnual(),
                request.periodicidad(),
                request.vigenteHasta()
        );
        simulacion = simulacionRepository.save(simulacion);
        List<SimulacionCuota> cuotas = planCuotasService.generar(
                simulacion.getId(),
                saldo,
                request.numeroCuotas(),
                request.tasaEfectivaAnual(),
                request.periodicidad(),
                request.fechaPrimeraCuota()
        );
        cuotaRepository.saveAll(cuotas);
        return SimulacionResponse.from(
                simulacion,
                cuotas.stream().map(SimulacionCuotaResponse::from).toList()
        );
    }

    @Transactional
    public SimulacionResponse enviar(
            ContextoUsuario contexto,
            Long simulacionId
    ) {
        Simulacion simulacion = obtenerParaActualizar(
                contexto,
                simulacionId
        );
        simulacion.enviar(ahora());
        return respuestaCompleta(simulacion);
    }

    @Transactional
    public SimulacionResponse cancelar(
            ContextoUsuario contexto,
            Long simulacionId
    ) {
        Simulacion simulacion = obtenerParaActualizar(
                contexto,
                simulacionId
        );
        simulacion.cancelar();
        return respuestaCompleta(simulacion);
    }

    @Transactional(readOnly = true)
    public Simulacion obtenerAutorizada(
            ContextoUsuario contexto,
            Long simulacionId
    ) {
        Simulacion simulacion = simulacionRepository
                .findByIdAndEmpresaId(simulacionId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La simulación no existe"
                ));
        Oportunidad oportunidad = oportunidadService.obtenerAutorizada(
                contexto,
                simulacion.getOportunidadId()
        );
        crmAlcanceService.verificarRegistro(
                contexto,
                simulacion.getProyectoId(),
                oportunidad.getAsesorId()
        );
        return simulacion;
    }

    private Simulacion obtenerParaActualizar(
            ContextoUsuario contexto,
            Long simulacionId
    ) {
        Simulacion simulacion = simulacionRepository
                .findForUpdate(simulacionId, contexto.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La simulación no existe"
                ));
        Oportunidad oportunidad = oportunidadService.obtenerAutorizada(
                contexto,
                simulacion.getOportunidadId()
        );
        crmAlcanceService.verificarRegistro(
                contexto,
                simulacion.getProyectoId(),
                oportunidad.getAsesorId()
        );
        return simulacion;
    }

    private Specification<Simulacion> filtroAlcance(
            ContextoUsuario contexto
    ) {
        AlcanceProyectos alcance = crmAlcanceService.obtener(contexto);
        return (root, query, cb) -> {
            Predicate empresa = cb.equal(
                    root.get("empresaId"),
                    contexto.empresaId()
            );
            if (alcance.empresaCompleta()) {
                return empresa;
            }
            List<Predicate> proyectosPermitidos = new ArrayList<>();
            if (!alcance.proyectos().isEmpty()) {
                proyectosPermitidos.add(
                        root.get("proyectoId").in(alcance.proyectos())
                );
            }
            if (alcance.registrosPropios()) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<Oportunidad> oportunidad =
                        subquery.from(Oportunidad.class);
                subquery.select(oportunidad.get("id")).where(
                        cb.equal(
                                oportunidad.get("empresaId"),
                                contexto.empresaId()
                        ),
                        cb.equal(
                                oportunidad.get("asesorId"),
                                contexto.usuarioId()
                        )
                );
                proyectosPermitidos.add(
                        root.get("oportunidadId").in(subquery)
                );
            }
            Predicate alcanceRegistro = proyectosPermitidos.isEmpty()
                    ? cb.disjunction()
                    : cb.or(proyectosPermitidos.toArray(Predicate[]::new));
            return cb.and(empresa, alcanceRegistro);
        };
    }

    private SimulacionResponse respuestaCompleta(Simulacion simulacion) {
        List<SimulacionCuotaResponse> cuotas = cuotaRepository
                .findBySimulacionIdOrderByNumeroAsc(simulacion.getId())
                .stream()
                .map(SimulacionCuotaResponse::from)
                .toList();
        return SimulacionResponse.from(simulacion, cuotas);
    }

    private static void validarFechas(
            LocalDate primeraCuota,
            LocalDateTime vigenteHasta
    ) {
        if (primeraCuota.isBefore(LocalDate.now(BOGOTA))) {
            throw new ReglaNegocioException(
                    "La primera cuota no puede estar en el pasado"
            );
        }
        if (!vigenteHasta.isAfter(ahora())) {
            throw new ReglaNegocioException(
                    "La vigencia de la simulación debe estar en el futuro"
            );
        }
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String generarCodigo() {
        return "SIM-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(BOGOTA);
    }
}
