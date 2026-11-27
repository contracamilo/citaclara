package es.citaclara.cita;

import es.citaclara.clinica.Especialidades;
import es.citaclara.clinica.HorarioAtencion;
import es.citaclara.clinica.Profesional;
import es.citaclara.clinica.RepositorioHorario;
import es.citaclara.clinica.RepositorioProfesional;
import es.citaclara.clinica.RepositorioServicio;
import es.citaclara.clinica.Servicio;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import es.citaclara.comun.errores.Restricciones;
import es.citaclara.paciente.Paciente;
import es.citaclara.paciente.RepositorioPaciente;
import es.citaclara.paciente.ServicioPacientes;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Alta de cita (FR-009–FR-020) con las reglas en el orden de data-model.md, en una única
 * transacción que incluye la ficha de un paciente nuevo (D12). La comprobación previa de solape da
 * el mensaje amable; la garantía es la restricción cita_sin_solape (D2), cuya violación por una
 * carrera se traduce a HUECO_RECIEN_OCUPADO (US2-AC5).
 */
@Service
public class ServicioAltaCita {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int INTENTOS_MAXIMOS = 5;

    private final RepositorioProfesional profesionales;
    private final RepositorioServicio servicios;
    private final RepositorioPaciente pacientes;
    private final RepositorioHorario horarios;
    private final RepositorioCita citas;
    private final ServicioPacientes servicioPacientes;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public ServicioAltaCita(RepositorioProfesional profesionales, RepositorioServicio servicios,
                            RepositorioPaciente pacientes, RepositorioHorario horarios, RepositorioCita citas,
                            ServicioPacientes servicioPacientes, PlatformTransactionManager transacciones,
                            Clock reloj) {
        this.profesionales = profesionales;
        this.servicios = servicios;
        this.pacientes = pacientes;
        this.horarios = horarios;
        this.citas = citas;
        this.servicioPacientes = servicioPacientes;
        this.transaccion = new TransactionTemplate(transacciones);
        this.reloj = reloj;
    }

    public CitaRespuesta crear(Long clinicaId, NuevaCitaPeticion peticion) {
        for (int intento = 1; ; intento++) {
            try {
                return crearUnaVez(clinicaId, peticion);
            } catch (DataAccessException error) {
                // Interbloqueo entre altas simultáneas sobre el mismo hueco: se reintenta y la
                // comprobación previa o la restricción dan el rechazo claro (o el hueco quedó libre).
                if (!Restricciones.esInterbloqueo(error)) {
                    throw error;
                }
                if (intento >= INTENTOS_MAXIMOS) {
                    throw new ErrorNegocio(HttpStatus.CONFLICT, "HUECO_RECIEN_OCUPADO",
                            Restricciones.MENSAJE_HUECO_RECIEN_OCUPADO);
                }
            }
        }
    }

    private CitaRespuesta crearUnaVez(Long clinicaId, NuevaCitaPeticion peticion) {
        try {
            return transaccion.execute(estado -> crearEnTransaccion(clinicaId, peticion));
        } catch (DataIntegrityViolationException error) {
            if (Restricciones.esSolape(error)) {
                throw new ErrorNegocio(HttpStatus.CONFLICT, "HUECO_RECIEN_OCUPADO",
                        Restricciones.MENSAJE_HUECO_RECIEN_OCUPADO);
            }
            if (Restricciones.esTelefonoDuplicado(error) && peticion.nuevoPaciente() != null) {
                throw servicioPacientes.duplicadoDe(clinicaId, peticion.nuevoPaciente().telefono());
            }
            throw error;
        }
    }

    private CitaRespuesta crearEnTransaccion(Long clinicaId, NuevaCitaPeticion p) {
        Instant ahora = reloj.instant();

        // 1. Recursos de la clínica de la sesión (FR-001, FR-003). El profesional se bloquea para
        //    serializar sus altas simultáneas (la garantía de RN1 sigue siendo cita_sin_solape).
        Profesional profesional = profesionales.bloquear(p.profesionalId(), clinicaId)
                .orElseThrow(ErrorNegocio::noEncontrado);
        Servicio servicio = servicios.findByIdAndClinicaId(p.servicioId(), clinicaId)
                .orElseThrow(ErrorNegocio::noEncontrado);

        // 2. Paciente existente o nuevo (FR-019, FR-020).
        Paciente paciente = p.pacienteId() != null
                ? pacientes.findByIdAndClinicaId(p.pacienteId(), clinicaId).orElseThrow(ErrorNegocio::noEncontrado)
                : servicioPacientes.crear(clinicaId, p.nuevoPaciente());

        // 3. Servicio de la especialidad del profesional (FR-010).
        if (!servicio.getEspecialidad().equals(profesional.getEspecialidad())) {
            throw new ErrorNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "SERVICIO_NO_DISPONIBLE",
                    profesional.getNombre() + " no ofrece ese servicio. Elige uno de "
                            + Especialidades.enFrase(profesional.getEspecialidad()) + ".");
        }

        // 4. Hora local válida, múltiplo de 5 minutos y existente en Madrid (D4).
        LocalTime hora = LocalTime.parse(p.horaInicio(), HORA);
        if (hora.getMinute() % 5 != 0) {
            throw new ErrorNegocio(HttpStatus.BAD_REQUEST, "HORA_NO_VALIDA", Mensajes.HORA_NO_VALIDA);
        }
        Instant inicio = ZonaClinica.aInstante(p.fecha(), hora);
        Instant fin = inicio.plusSeconds(servicio.getDuracionMinutos() * 60L);

        // 5. RN2: el inicio debe ser posterior al momento actual (FR-014).
        if (!inicio.isAfter(ahora)) {
            throw new ErrorNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "HORA_PASADA",
                    "No se pueden crear citas en el pasado. Elige una hora posterior.");
        }

        // 6. Dentro del horario de atención de ese día (FR-015).
        HorarioAtencion horario = horarios.delDia(clinicaId, p.fecha().getDayOfWeek().getValue())
                .orElseThrow(() -> new ErrorNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "FUERA_DE_HORARIO",
                        "La clínica no atiende ese día."));
        Instant apertura = ZonaClinica.aInstante(p.fecha(), horario.getApertura());
        Instant cierre = ZonaClinica.aInstante(p.fecha(), horario.getCierre());
        if (inicio.isBefore(apertura) || fin.isAfter(cierre)) {
            throw new ErrorNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "FUERA_DE_HORARIO",
                    "La cita debe empezar y terminar dentro del horario de atención ("
                            + ZonaClinica.formatoHora(horario.getApertura()) + " a "
                            + ZonaClinica.formatoHora(horario.getCierre()) + ").");
        }

        // 7. Comprobación previa de solape, para explicar con qué cita choca (FR-012, FR-016).
        List<Cita> solapadas = citas.ocupantesSolapadas(profesional.getId(), inicio, fin);
        if (!solapadas.isEmpty()) {
            Cita conflicto = solapadas.getFirst();
            throw new ErrorNegocio(HttpStatus.CONFLICT, "SOLAPE",
                    profesional.getNombre() + " ya tiene una cita de " + ZonaClinica.formatoHora(conflicto.getInicio())
                            + " a " + ZonaClinica.formatoHora(conflicto.getFin()) + ". Elige otro hueco.")
                    .con("citaConflictiva", CitaRespuesta.desde(conflicto, ahora));
        }

        // 8. Inserción: la restricción de la base de datos es la garantía final (D2).
        Cita cita = citas.saveAndFlush(new Cita(clinicaId, profesional, servicio, paciente, inicio, fin,
                servicio.getPrecioCentimos(), EstadoCita.RESERVADA, ahora));
        return CitaRespuesta.desde(cita, ahora);
    }
}
