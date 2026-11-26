package es.citaclara.paciente;

import es.citaclara.comun.TelefonoValido;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ficha creada durante el alta de una cita (FR-019). */
public record NuevoPaciente(
        @NotBlank(message = "Escribe el nombre del paciente.")
        @Size(max = 120, message = "El nombre puede tener como máximo 120 caracteres.")
        String nombre,
        @NotBlank(message = "Escribe el teléfono del paciente.")
        @TelefonoValido
        String telefono,
        @Email(message = "Escribe un email válido, por ejemplo ana@ejemplo.es.")
        @Size(max = 254, message = "El email puede tener como máximo 254 caracteres.")
        String email) {
}
