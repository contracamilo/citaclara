package es.citaclara.clinica;

/** Esquema {@code Profesional} de contracts/api.yaml. */
public record ProfesionalRespuesta(long id, String nombre, String especialidad, String especialidadNombre) {

    public static ProfesionalRespuesta desde(Profesional p) {
        return new ProfesionalRespuesta(p.getId(), p.getNombre(), p.getEspecialidad(),
                Especialidades.nombre(p.getEspecialidad()));
    }
}
