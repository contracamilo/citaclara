package es.citaclara.semilla;

import java.time.LocalTime;
import java.util.List;

/** Datos fijos de Clínica Eleva (FR-030). Todos los datos personales son ficticios. */
public final class CatalogoDemostracion {

    public static final String IDENTIFICADOR = "eleva";
    public static final String NOMBRE = "Clínica Eleva";
    /** Clave de demostración documentada; pública por diseño (research D16). */
    public static final String CLAVE = "eleva-demo-2026";
    public static final LocalTime APERTURA = LocalTime.of(9, 0);
    public static final LocalTime CIERRE = LocalTime.of(20, 0);

    public record ProfesionalDemo(String nombre, String especialidad) {
    }

    /** {@code primeraVisita}: servicio de primera visita de su especialidad (≈ 20 % de las citas). */
    public record ServicioDemo(String nombre, String especialidad, int minutos, int precioCentimos, boolean primeraVisita) {
    }

    public record PacienteDemo(String nombre, String telefono, String email) {
    }

    public static final List<ProfesionalDemo> PROFESIONALES = List.of(
            new ProfesionalDemo("María", "fisioterapia"),
            new ProfesionalDemo("Jorge", "fisioterapia"),
            new ProfesionalDemo("Lucía", "nutricion"));

    public static final List<ServicioDemo> SERVICIOS = List.of(
            new ServicioDemo("Sesión fisioterapia", "fisioterapia", 45, 4000, false),
            new ServicioDemo("Primera visita fisioterapia", "fisioterapia", 60, 5000, true),
            new ServicioDemo("Consulta nutrición", "nutricion", 30, 3500, false),
            new ServicioDemo("Primera visita nutrición", "nutricion", 45, 4500, true));

    private static final String[] NOMBRES = {
            "Ana Pérez", "Carlos Gómez", "Lucía Fernández", "Javier Martín", "Marta Sánchez",
            "David López", "Elena Ruiz", "Pablo Díaz", "Carmen Moreno", "Sergio Álvarez",
            "Laura Romero", "Daniel Navarro", "Paula Torres", "Alberto Domínguez", "Sara Vázquez",
            "Raúl Ramos", "Cristina Gil", "Iván Serrano", "Beatriz Blanco", "Óscar Molina",
            "Nuria Castro", "Adrián Ortega", "Irene Delgado", "Rubén Marín", "Silvia Rubio",
            "Hugo Sanz", "Alicia Iglesias", "Mario Medina", "Rocío Garrido", "Diego Cortés",
            "Patricia Castillo", "Álvaro Santos", "Noelia Lozano", "Víctor Guerrero", "Inés Cano",
            "Jaime Prieto", "Teresa Méndez", "Gonzalo Cruz", "Claudia Herrera", "Manuel Peña"};

    public static final List<PacienteDemo> PACIENTES = construirPacientes();

    private CatalogoDemostracion() {
    }

    private static List<PacienteDemo> construirPacientes() {
        PacienteDemo[] pacientes = new PacienteDemo[NOMBRES.length];
        for (int i = 0; i < NOMBRES.length; i++) {
            String telefono = String.format("+346000%05d", 101 + i * 37);
            pacientes[i] = new PacienteDemo(NOMBRES[i], telefono, email(NOMBRES[i]));
        }
        return List.of(pacientes);
    }

    private static String email(String nombre) {
        String sinTildes = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        return sinTildes.replace(' ', '.') + "@ejemplo.es";
    }
}
