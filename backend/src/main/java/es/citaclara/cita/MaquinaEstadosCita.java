package es.citaclara.cita;

import es.citaclara.comun.errores.ErrorNegocio;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * Máquina de estados de la cita (FR-021–FR-023, data-model.md):
 * <pre>
 * RESERVADA ── cancelar          [ahora &lt; inicio] ──▶ CANCELADA
 *           ── completar         [ahora ≥ inicio] ──▶ COMPLETADA
 *           ── marcar no asist.  [ahora ≥ inicio] ──▶ NO_ASISTIDA
 * COMPLETADA, CANCELADA, NO_ASISTIDA: finales
 * </pre>
 * Ninguna transición pasa de un estado libre a uno ocupante: un cambio de estado nunca crea solapes.
 */
public final class MaquinaEstadosCita {

    static final String CERRADA = "Esta cita ya está cerrada y no admite cambios.";
    static final String YA_EMPEZADA =
            "Esta cita ya ha empezado: no se puede cancelar. Márcala como completada o no asistida.";
    static final String AUN_NO_EMPEZADA = "Esta cita aún no ha empezado: solo se puede cancelar.";

    private MaquinaEstadosCita() {
    }

    public static List<Accion> accionesDisponibles(EstadoCita estado, Instant inicio, Instant ahora) {
        if (estado != EstadoCita.RESERVADA) {
            return List.of();
        }
        return ahora.isBefore(inicio)
                ? List.of(Accion.CANCELAR)
                : List.of(Accion.COMPLETAR, Accion.MARCAR_NO_ASISTIDA);
    }

    public static EstadoCita transicion(EstadoCita estado, Instant inicio, Accion accion, Instant ahora) {
        if (estado != EstadoCita.RESERVADA) {
            throw noDisponible(CERRADA);
        }
        boolean empezada = !ahora.isBefore(inicio);
        return switch (accion) {
            case CANCELAR -> {
                if (empezada) {
                    throw noDisponible(YA_EMPEZADA);
                }
                yield EstadoCita.CANCELADA;
            }
            case COMPLETAR, MARCAR_NO_ASISTIDA -> {
                if (!empezada) {
                    throw noDisponible(AUN_NO_EMPEZADA);
                }
                yield accion == Accion.COMPLETAR ? EstadoCita.COMPLETADA : EstadoCita.NO_ASISTIDA;
            }
        };
    }

    private static ErrorNegocio noDisponible(String mensaje) {
        return new ErrorNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "ACCION_NO_DISPONIBLE", mensaje);
    }
}
