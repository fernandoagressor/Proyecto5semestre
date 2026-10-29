package com.inmobiliaria.inmobiliaria_api.service.simulacion;

import static org.springframework.data.mongodb.core.query.Criteria.where;

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
import com.inmobiliaria.inmobiliaria_api.repository.simulacion.SimulacionRepository;
import com.inmobiliaria.inmobiliaria_api.security.ContextoUsuario;
import com.inmobiliaria.inmobiliaria_api.service.compartido.SecuenciaService;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.ConflictoException;
import com.inmobiliaria.inmobiliaria_api.service.compartido.excepcion.RecursoNoEncontradoException;
import com.inmobiliaria.inmobiliaria_api.service.crm.CrmAlcanceService;
import com.inmobiliaria.inmobiliaria_api.service.crm.OportunidadService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulaciones de financiación. Desde la rama 8b viven en la colección {@code simulaciones} de
 * MongoDB con el cronograma embebido. La aceptación la aplica la reserva por la cola
 * {@code crm_pendientes}.
 */
@Service
public class SimulacionService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    static final String SECUENCIA = "simulaciones";

    private final SimulacionRepository simulacionRepository;
    private final LoteRepository loteRepository;
    private final OportunidadService oportunidadService;
    private final CrmAlcanceService crmAlcanceService;
    private final PlanCuotasService planCuotasService;
    private final SecuenciaService secuencias;

    public SimulacionService(
            SimulacionRepository simulacionRepository,
            LoteRepository loteRepository,
            OportunidadService oportunidadService,
            CrmAlcanceService crmAlcanceService,
            PlanCuotasService planCuotasService,
            SecuenciaService secuencias
    ) {
        this.simulacionRepository = simulacionRepository;
        this.loteRepository = loteRepository;
        this.oportunidadService = oportunidadService;
        this.crmAlcanceService = crmAlcanceService;
        this.planCuotasService = planCuotasService;
        this.secuencias = secuencias;
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
        List<Criteria> condiciones = new ArrayList<>();
        condiciones.add(crmAlcanceService.filtroMongo(contexto));
        if (estado != null) condiciones.add(where("estado").is(estado.name()));
        if (proyectoId != null) condiciones.add(where("proyectoId").is(proyectoId));
        if (oportunidadId != null) condiciones.add(where("oportunidadId").is(oportunidadId));
        var resultado = simulacionRepository.buscarResumen(
                new Criteria().andOperator(condiciones),
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100),
                        Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"))
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
        simulacion.identificar(secuencias.siguiente(SECUENCIA));
        simulacion.asignarAsesor(oportunidad.getAsesorId());
        List<SimulacionCuota> cuotas = planCuotasService.generar(
                simulacion.getId(),
                saldo,
                request.numeroCuotas(),
                request.tasaEfectivaAnual(),
                request.periodicidad(),
                request.fechaPrimeraCuota()
        );
        simulacion.asignarCronograma(cuotas);
        try {
            simulacion = simulacionRepository.insertar(simulacion);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("No fue posible asignar un código único a la simulación");
        }
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
        return respuestaCompleta(guardar(simulacion));
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
        return respuestaCompleta(guardar(simulacion));
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

    private Simulacion guardar(Simulacion simulacion) {
        try {
            return simulacionRepository.guardar(simulacion);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictoException("La simulación fue modificada por otro usuario");
        }
    }

    private SimulacionResponse respuestaCompleta(Simulacion simulacion) {
        List<SimulacionCuotaResponse> cuotas = simulacion.getCuotas()
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
