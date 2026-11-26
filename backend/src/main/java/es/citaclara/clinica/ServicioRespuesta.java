package es.citaclara.clinica;

/** Esquema {@code Servicio} de contracts/api.yaml; precio en céntimos enteros (D5). */
public record ServicioRespuesta(long id, String nombre, String especialidad, String especialidadNombre,
                                int duracionMinutos, int precioCentimos) {

    public static ServicioRespuesta desde(Servicio s) {
        return new ServicioRespuesta(s.getId(), s.getNombre(), s.getEspecialidad(),
                Especialidades.nombre(s.getEspecialidad()), s.getDuracionMinutos(), s.getPrecioCentimos());
    }
}
