package es.citaclara.cita;

import es.citaclara.comun.ZonaClinica;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Forma exacta del esquema {@code Cita} de contracts/api.yaml; horas locales de Madrid (D4). */
public record CitaRespuesta(
        long id,
        LocalDate fecha,
        String horaInicio,
        String horaFin,
        Ref profesional,
        PacienteRef paciente,
        Ref servicio,
        int importeCentimos,
        EstadoCita estado,
        List<Accion> acciones,
        int version) {

    public record Ref(long id, String nombre) {
    }

    public record PacienteRef(long id, String nombre, String telefono) {
    }

    /** Las acciones disponibles las decide el servidor con su reloj (research D8). */
    public static CitaRespuesta desde(Cita cita, Instant ahora) {
        return new CitaRespuesta(
                cita.getId(),
                ZonaClinica.fechaLocal(cita.getInicio()),
                ZonaClinica.formatoHora(cita.getInicio()),
                ZonaClinica.formatoHora(cita.getFin()),
                new Ref(cita.getProfesional().getId(), cita.getProfesional().getNombre()),
                new PacienteRef(cita.getPaciente().getId(), cita.getPaciente().getNombre(),
                        cita.getPaciente().getTelefono()),
                new Ref(cita.getServicio().getId(), cita.getServicio().getNombre()),
                cita.getImporteCentimos(),
                cita.getEstado(),
                MaquinaEstadosCita.accionesDisponibles(cita.getEstado(), cita.getInicio(), ahora),
                cita.getVersion());
    }
}
