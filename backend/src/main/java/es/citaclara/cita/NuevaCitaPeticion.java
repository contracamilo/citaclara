package es.citaclara.cita;

import es.citaclara.paciente.NuevoPaciente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/** Esquema {@code NuevaCita}: exactamente uno de {@code pacienteId} o {@code nuevoPaciente} (D12). */
public record NuevaCitaPeticion(
        @NotNull(message = "Elige un profesional.") Long profesionalId,
        @NotNull(message = "Elige un servicio.") Long servicioId,
        Long pacienteId,
        @Valid NuevoPaciente nuevoPaciente,
        @NotNull(message = "Elige el día de la cita.") LocalDate fecha,
        @NotNull(message = "Elige la hora de inicio.")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Elige una hora válida, por ejemplo 10:00.")
        String horaInicio) {

    @AssertTrue(message = "Elige un paciente o da de alta uno nuevo.")
    public boolean isPaciente() {
        return (pacienteId == null) != (nuevoPaciente == null);
    }
}
