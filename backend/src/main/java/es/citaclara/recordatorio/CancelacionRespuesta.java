package es.citaclara.recordatorio;

import java.time.LocalDate;

/**
 * Forma exacta del esquema {@code Cancelacion} de specs/003-recordatorios-cita/contracts/api.yaml.
 * Datos mínimos: sin importe, sin teléfono ni email del paciente y sin identificadores internos.
 */
public record CancelacionRespuesta(Situacion situacion, boolean canceladaPorPaciente, ClinicaRef clinica, CitaRef cita) {

    public enum Situacion {
        /** Reservada y cancelable (hasta 24 h antes del inicio). */
        ACTIVA,
        /** Reservada, pero a menos de 24 h o ya empezada. */
        FUERA_DE_PLAZO,
        /** Cancelada, completada o no asistida. */
        YA_CERRADA
    }

    public record ClinicaRef(String nombre, String telefono) {
    }

    public record CitaRef(LocalDate fecha, String horaInicio, String profesional, String servicio) {
    }
}
