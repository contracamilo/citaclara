package es.citaclara.cita;

import java.time.Instant;
import java.util.Optional;

/**
 * Lo que el portal del paciente (002) añade a la cancelación de una cita sin duplicar la máquina de
 * estados (research R7): propiedad, idempotencia, versión, plazo, registro del origen y la forma de
 * la respuesta y de los errores, que son los del portal y nunca los del panel.
 *
 * @param <R> respuesta del portal
 */
public interface ComprobacionPortal<R> {

    /**
     * Antes de la transición. Devuelve una respuesta si no hay nada que hacer (idempotencia) o lanza
     * el error del portal (404, 409, 422).
     */
    Optional<R> verificar(Cita cita, int versionVista, Instant ahora);

    /** Dentro de la misma transacción, después de guardar la cita ya cancelada. */
    void despuesDeCancelar(Cita cita, Instant ahora);

    /** Respuesta con la cita ya cancelada. */
    R resultado(Cita cita, Instant ahora);

    /**
     * Otra escritura simultánea ganó la carrera (bloqueo optimista). Devuelve una respuesta si la
     * ganadora fue una cancelación del mismo portal (idempotencia) o lanza el conflicto del portal.
     */
    R alPerderCarrera(Cita actual, Instant ahora);
}
