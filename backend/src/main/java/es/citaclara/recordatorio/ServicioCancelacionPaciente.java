package es.citaclara.recordatorio;

import es.citaclara.cita.CanceladaPor;
import es.citaclara.cita.Cita;
import es.citaclara.cita.EstadoCita;
import es.citaclara.cita.MaquinaEstadosCita;
import es.citaclara.cita.Accion;
import es.citaclara.cita.RepositorioCita;
import es.citaclara.clinica.Clinica;
import es.citaclara.clinica.RepositorioClinica;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.recordatorio.CancelacionRespuesta.CitaRef;
import es.citaclara.recordatorio.CancelacionRespuesta.ClinicaRef;
import es.citaclara.recordatorio.CancelacionRespuesta.Situacion;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Consulta y cancelación de una cita desde el enlace del recordatorio (FR-012–FR-019). El paciente
 * puede cancelar hasta 24 h antes del inicio (límite incluido); la transición reutiliza la máquina
 * de estados de la 001 y el bloqueo optimista de la cita (research D7).
 */
@Service
public class ServicioCancelacionPaciente {

    static final Duration PLAZO = Duration.ofHours(24);

    private final RepositorioCita citas;
    private final RepositorioClinica clinicas;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public ServicioCancelacionPaciente(RepositorioCita citas, RepositorioClinica clinicas, Clock reloj,
                                       PlatformTransactionManager transacciones) {
        this.citas = citas;
        this.clinicas = clinicas;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** No modifica nada: abrir el enlace nunca cancela (FR-012). */
    public CancelacionRespuesta consultar(String enlace) {
        Cita cita = localizar(enlace);
        return respuesta(cita, clinica(cita), reloj.instant());
    }

    public CancelacionRespuesta cancelar(String enlace) {
        try {
            return transaccion.execute(estado -> {
                Cita cita = localizar(enlace);
                Clinica clinica = clinica(cita);
                Instant ahora = reloj.instant();
                if (canceladaPorElPaciente(cita)) {
                    return respuesta(cita, clinica, ahora); // repetir la confirmación es inocuo (US2-AC7)
                }
                if (cita.getEstado() != EstadoCita.RESERVADA) {
                    throw yaNoActiva(cita, clinica, ahora);
                }
                if (situacion(cita, ahora) != Situacion.ACTIVA) {
                    throw new ErrorNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "FUERA_DE_PLAZO",
                            "Ya no puedes cancelar desde aquí. Llama a la clínica al " + clinica.getTelefono() + ".")
                            .con("cancelacion", respuesta(cita, clinica, ahora));
                }
                MaquinaEstadosCita.transicion(cita.getEstado(), cita.getInicio(), Accion.CANCELAR, ahora);
                cita.cancelarPorPaciente();
                citas.saveAndFlush(cita);
                return respuesta(cita, clinica, ahora);
            });
        } catch (ObjectOptimisticLockingFailureException carrera) {
            // Otro cambio simultáneo se aplicó primero: se relee la cita real en una transacción nueva (FR-019).
            return transaccion.execute(estado -> {
                Cita actual = localizar(enlace);
                Clinica clinica = clinica(actual);
                Instant ahora = reloj.instant();
                if (canceladaPorElPaciente(actual)) {
                    return respuesta(actual, clinica, ahora);
                }
                throw yaNoActiva(actual, clinica, ahora);
            });
        }
    }

    private Cita localizar(String enlace) {
        if (enlace == null || !EnlaceCancelacion.FORMATO.matcher(enlace).matches()) {
            throw ErrorNegocio.noEncontrado();
        }
        return citas.buscarPorHuella(EnlaceCancelacion.huella(enlace)).orElseThrow(ErrorNegocio::noEncontrado);
    }

    private Clinica clinica(Cita cita) {
        return clinicas.findById(cita.getClinicaId()).orElseThrow(ErrorNegocio::noEncontrado);
    }

    private static boolean canceladaPorElPaciente(Cita cita) {
        return cita.getEstado() == EstadoCita.CANCELADA && cita.getCanceladaPor() == CanceladaPor.PACIENTE;
    }

    static Situacion situacion(Cita cita, Instant ahora) {
        if (cita.getEstado() != EstadoCita.RESERVADA) {
            return Situacion.YA_CERRADA;
        }
        return ahora.isAfter(cita.getInicio().minus(PLAZO)) ? Situacion.FUERA_DE_PLAZO : Situacion.ACTIVA;
    }

    private static CancelacionRespuesta respuesta(Cita cita, Clinica clinica, Instant ahora) {
        return new CancelacionRespuesta(situacion(cita, ahora), canceladaPorElPaciente(cita),
                new ClinicaRef(clinica.getNombre(), clinica.getTelefono()),
                new CitaRef(ZonaClinica.fechaLocal(cita.getInicio()), ZonaClinica.formatoHora(cita.getInicio()),
                        cita.getProfesional().getNombre(), cita.getServicio().getNombre()));
    }

    private static ErrorNegocio yaNoActiva(Cita cita, Clinica clinica, Instant ahora) {
        return new ErrorNegocio(HttpStatus.CONFLICT, "ESTADO_YA_CAMBIADO",
                "Esta cita ya no está activa. Si tienes dudas, llama a la clínica.")
                .con("cancelacion", respuesta(cita, clinica, ahora));
    }
}
