package es.citaclara.paciente;

/** Esquema {@code Paciente} de contracts/api.yaml. */
public record PacienteRespuesta(long id, String nombre, String telefono, String email) {

    public static PacienteRespuesta desde(Paciente p) {
        return new PacienteRespuesta(p.getId(), p.getNombre(), p.getTelefono(), p.getEmail());
    }
}
