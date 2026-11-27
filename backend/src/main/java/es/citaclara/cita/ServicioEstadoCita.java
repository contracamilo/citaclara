package es.citaclara.cita;

import es.citaclara.comun.errores.ErrorNegocio;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * Cambio de estado de una cita reservada (FR-021–FR-025). Bloqueo optimista con la versión que vio
 * la recepcionista (research D3): si otra persona cambió la cita antes, se responde con su estado real.
 */
@Service
public class ServicioEstadoCita {

    private final RepositorioCita citas;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public ServicioEstadoCita(RepositorioCita citas, PlatformTransactionManager transacciones, Clock reloj) {
        this.citas = citas;
        this.transaccion = new TransactionTemplate(transacciones);
        this.reloj = reloj;
    }

    public CitaRespuesta cambiar(Long clinicaId, Long citaId, Accion accion, int versionVista) {
        try {
            return transaccion.execute(estado -> {
                Instant ahora = reloj.instant();
                Cita cita = citas.buscar(citaId, clinicaId).orElseThrow(ErrorNegocio::noEncontrado);
                if (cita.getVersion() != versionVista) {
                    throw yaCambiada(cita, ahora);
                }
                cita.cambiarEstado(MaquinaEstadosCita.transicion(cita.getEstado(), cita.getInicio(), accion, ahora));
                citas.saveAndFlush(cita);
                return CitaRespuesta.desde(cita, ahora);
            });
        } catch (ObjectOptimisticLockingFailureException carrera) {
            // Otro cambio simultáneo se aplicó primero: se relee la cita real en una transacción nueva.
            throw transaccion.execute(estado -> {
                Cita actual = citas.buscar(citaId, clinicaId).orElseThrow(ErrorNegocio::noEncontrado);
                return yaCambiada(actual, reloj.instant());
            });
        }
    }

    /**
     * Cancelación desde el portal del paciente (002, research R7). Misma transición
     * ({@link MaquinaEstadosCita}) y mismo bloqueo optimista que {@link #cambiar}, pero las
     * comprobaciones previas, el registro del origen y los errores los aporta el portal: aquí no se
     * usa {@link #yaCambiada}, cuyo mensaje y formato son los del panel.
     */
    public <R> R cancelarDesdePortal(Long clinicaId, Long citaId, int versionVista, ComprobacionPortal<R> portal) {
        try {
            return transaccion.execute(estado -> {
                Instant ahora = reloj.instant();
                Cita cita = citas.buscar(citaId, clinicaId).orElseThrow(ErrorNegocio::noEncontrado);
                java.util.Optional<R> previa = portal.verificar(cita, versionVista, ahora);
                if (previa.isPresent()) {
                    return previa.get();
                }
                cita.cambiarEstado(MaquinaEstadosCita.transicion(cita.getEstado(), cita.getInicio(), Accion.CANCELAR, ahora));
                // Primero la cita (bloqueo optimista) y después el registro: si dos cancelaciones compiten,
                // la perdedora falla en la versión y nunca en la clave del registro.
                citas.saveAndFlush(cita);
                portal.despuesDeCancelar(cita, ahora);
                return portal.resultado(cita, ahora);
            });
        } catch (ObjectOptimisticLockingFailureException carrera) {
            return transaccion.execute(estado -> {
                Cita actual = citas.buscar(citaId, clinicaId).orElseThrow(ErrorNegocio::noEncontrado);
                return portal.alPerderCarrera(actual, reloj.instant());
            });
        }
    }

    private static ErrorNegocio yaCambiada(Cita actual, Instant ahora) {
        return new ErrorNegocio(HttpStatus.CONFLICT, "ESTADO_YA_CAMBIADO",
                "Otra persona acaba de cambiar esta cita. Ahora está: " + actual.getEstado().nombre() + ".")
                .con("citaActual", CitaRespuesta.desde(actual, ahora));
    }
}
