package es.citaclara.semilla;

import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.HOY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-033 y puerta de merge 5: las cifras de specs/001-nucleo-agenda/datos-demostracion.md se
 * reproducen con la semilla. Las cifras se calculan desde la base de datos tras cargar la semilla.
 * Para regenerar el documento tras un cambio deliberado de la semilla:
 * {@code ./mvnw test -Dtest=DatosDemostracionDocumentadosTest -DactualizarDatosDemostracion=true}
 */
class DatosDemostracionDocumentadosTest extends PruebaIntegracionBase {

    private static final Path DOCUMENTO = Path.of("..", "specs", "001-nucleo-agenda", "datos-demostracion.md");
    private static final String INICIO = "<!-- recuentos:inicio -->";
    private static final String FIN = "<!-- recuentos:fin -->";

    @Autowired
    private CargadorSemilla cargador;

    @Test
    @DisplayName("[001][FR-033] los recuentos documentados de la semilla coinciden con los datos cargados")
    void recuentosDocumentados() throws IOException {
        cargador.cargar();
        String calculado = recuentos();

        if (Boolean.getBoolean("actualizarDatosDemostracion")) {
            String actual = Files.readString(DOCUMENTO, StandardCharsets.UTF_8);
            Files.writeString(DOCUMENTO, actual.substring(0, actual.indexOf(INICIO) + INICIO.length()) + "\n"
                    + calculado + actual.substring(actual.indexOf(FIN)), StandardCharsets.UTF_8);
        }
        String documento = Files.readString(DOCUMENTO, StandardCharsets.UTF_8);
        String documentado = documento.substring(documento.indexOf(INICIO) + INICIO.length(), documento.indexOf(FIN)).strip();
        assertThat(documentado).as("datos-demostracion.md no coincide con la semilla").isEqualTo(calculado.strip());
        assertThat(documento).doesNotContainPattern("\\b20\\d\\d-\\d\\d-\\d\\d\\b").doesNotContain("semana −");
    }

    private String recuentos() {
        StringBuilder md = new StringBuilder();
        String historia = "c.inicio < :hoy";
        String futuro = "c.inicio >= :hoy";
        int totalHistoria = entero("SELECT count(*) FROM cita c WHERE " + historia);
        int totalFuturo = entero("SELECT count(*) FROM cita c WHERE " + futuro);

        md.append("### Totales\n\n| Recuento | Citas | Importe |\n|---|---:|---:|\n");
        md.append(fila("Historia (días laborables −40 a −1)", totalHistoria, importe(historia)));
        md.append(fila("Futuro (días laborables +1 a +10)", totalFuturo, importe(futuro)));
        md.append(fila("Total", totalHistoria + totalFuturo, importe("true")));

        md.append("\n### Historia por estado\n\n| Estado | Citas | % de la historia | Importe |\n|---|---:|---:|---:|\n");
        for (String[] estado : new String[][]{{"COMPLETADA", "Completada"}, {"NO_ASISTIDA", "No asistida"}, {"CANCELADA", "Cancelada"}}) {
            String condicion = historia + " AND c.estado = '" + estado[0] + "'";
            int n = entero("SELECT count(*) FROM cita c WHERE " + condicion);
            md.append("| ").append(estado[1]).append(" | ").append(n).append(" | ")
                    .append(String.format("%.1f %%", n * 100.0 / totalHistoria).replace('.', ',')).append(" | ")
                    .append(euros(importe(condicion))).append(" |\n");
        }

        md.append("\n### Por profesional\n\n| Profesional | Historia | Completadas | No asistidas | Canceladas | Futuro (reservadas) | Importe completado |\n|---|---:|---:|---:|---:|---:|---:|\n");
        for (String profesional : List.of("María", "Jorge", "Lucía")) {
            String de = " AND p.nombre = '" + profesional + "'";
            md.append("| ").append(profesional).append(" | ")
                    .append(entero(conProfesional(historia + de))).append(" | ")
                    .append(entero(conProfesional(historia + " AND c.estado = 'COMPLETADA'" + de))).append(" | ")
                    .append(entero(conProfesional(historia + " AND c.estado = 'NO_ASISTIDA'" + de))).append(" | ")
                    .append(entero(conProfesional(historia + " AND c.estado = 'CANCELADA'" + de))).append(" | ")
                    .append(entero(conProfesional(futuro + de))).append(" | ")
                    .append(euros(importe(historia + " AND c.estado = 'COMPLETADA' AND c.profesional_id = "
                            + "(SELECT id FROM profesional WHERE nombre = '" + profesional + "')"))).append(" |\n");
        }

        md.append("\n### Citas de ejemplo (primera cita del día de cada profesional)\n\n| Día laborable | Profesional | Horario | Paciente | Servicio | Importe | Estado |\n|---|---|---|---|---|---:|---|\n");
        for (Object[] ejemplo : new Object[][]{{-3, "María"}, {-1, "Lucía"}, {1, "María"}, {2, "Jorge"}}) {
            md.append(ejemplo((int) ejemplo[0], (String) ejemplo[1]));
        }
        return md.toString();
    }

    private String ejemplo(int posicion, String profesional) {
        LocalDate fecha = DiasLaborables.fecha(HOY, posicion);
        Map<String, Object> c = jdbc.queryForMap("""
                SELECT to_char(c.inicio AT TIME ZONE 'Europe/Madrid', 'HH24:MI') || '–' ||
                       to_char(c.fin AT TIME ZONE 'Europe/Madrid', 'HH24:MI') AS horario,
                       pa.nombre AS paciente, s.nombre AS servicio, c.importe_centimos AS importe, c.estado
                FROM cita c JOIN profesional p ON p.id = c.profesional_id
                JOIN paciente pa ON pa.id = c.paciente_id JOIN servicio s ON s.id = c.servicio_id
                WHERE p.nombre = ? AND (c.inicio AT TIME ZONE 'Europe/Madrid')::date = ?
                ORDER BY c.inicio LIMIT 1
                """, profesional, Date.valueOf(fecha));
        String estado = switch ((String) c.get("estado")) {
            case "COMPLETADA" -> "Completada";
            case "NO_ASISTIDA" -> "No asistida";
            case "CANCELADA" -> "Cancelada";
            default -> "Reservada";
        };
        return "| " + (posicion > 0 ? "+" : "−") + Math.abs(posicion) + " | " + profesional + " | " + c.get("horario")
                + " | " + c.get("paciente") + " | " + c.get("servicio") + " | "
                + euros(((Number) c.get("importe")).longValue()) + " | " + estado + " |\n";
    }

    private String conProfesional(String condicion) {
        return "SELECT count(*) FROM cita c JOIN profesional p ON p.id = c.profesional_id WHERE " + condicion;
    }

    private long importe(String condicion) {
        return jdbc.queryForObject(("SELECT coalesce(sum(c.importe_centimos), 0) FROM cita c WHERE " + condicion)
                .replace(":hoy", "'" + es.citaclara.comun.ZonaClinica.inicioDelDia(HOY) + "'"), Long.class);
    }

    private int entero(String sql) {
        return jdbc.queryForObject(sql.replace(":hoy", "'" + es.citaclara.comun.ZonaClinica.inicioDelDia(HOY) + "'"),
                Integer.class);
    }

    private static String fila(String nombre, int citas, long centimos) {
        return "| " + nombre + " | " + citas + " | " + euros(centimos) + " |\n";
    }

    /**
     * Mismo criterio que el formateador único del frontend (Intl es-ES, CLDR): separador de miles
     * solo a partir de 5 cifras enteras («1234,50 €», «12.345,50 €»), con la cifra en céntimos.
     */
    static String euros(long centimos) {
        String entero = Long.toString(centimos / 100);
        if (entero.length() >= 5) {
            StringBuilder agrupado = new StringBuilder();
            for (int i = 0; i < entero.length(); i++) {
                if (i > 0 && (entero.length() - i) % 3 == 0) {
                    agrupado.append('.');
                }
                agrupado.append(entero.charAt(i));
            }
            entero = agrupado.toString();
        }
        return entero + "," + String.format("%02d", centimos % 100) + " € (" + centimos + " céntimos)";
    }
}
