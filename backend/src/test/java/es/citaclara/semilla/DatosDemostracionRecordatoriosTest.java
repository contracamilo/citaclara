package es.citaclara.semilla;

import es.citaclara.recordatorio.ServicioRecordatorios;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static es.citaclara.soporte.DatosPrueba.HOY;
import static es.citaclara.soporte.DatosPrueba.MADRID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SC-004 y puerta de merge 5: las cifras de specs/003-recordatorios-cita/datos-demostracion.md se
 * reproducen con la semilla y el proceso diario. Regenerar tras un cambio deliberado:
 * {@code ./mvnw test -Dtest=DatosDemostracionRecordatoriosTest -DactualizarDatosDemostracion=true}
 */
class DatosDemostracionRecordatoriosTest extends PruebaRecordatoriosBase {

    private static final Path DOCUMENTO = Path.of("..", "specs", "003-recordatorios-cita", "datos-demostracion.md");
    private static final String INICIO = "<!-- recuentos:inicio -->";
    private static final String FIN = "<!-- recuentos:fin -->";

    @Autowired
    private CargadorSemilla cargador;

    @Autowired
    private ServicioRecordatorios servicio;

    @Test
    @DisplayName("[003][SC-004][SC-001][US1-AC1] los recordatorios por día laborable coinciden con lo documentado y una repetición no genera ninguno")
    void recuentosDocumentados() throws IOException {
        cargador.cargar();
        int totalReservadas = jdbc.queryForObject("SELECT count(*) FROM cita WHERE estado = 'RESERVADA'", Integer.class);

        // Proceso diario a las 09:00 de cada día natural desde el día de carga, durante 16 días.
        int generados = 0;
        for (int dia = 0; dia < 16; dia++) {
            reloj.fijar(ZonedDateTime.of(HOY.plusDays(dia), LocalTime.of(9, 0), MADRID));
            generados += servicio.generar();
        }
        assertThat(generados).as("una ejecución diaria cubre todas las citas futuras exactamente una vez").isEqualTo(totalReservadas);

        // Repetir las mismas ejecuciones no genera nada (SC-001).
        for (int dia = 0; dia < 16; dia++) {
            reloj.fijar(ZonedDateTime.of(HOY.plusDays(dia), LocalTime.of(9, 0), MADRID));
            assertThat(servicio.generar()).isZero();
        }

        String calculado = recuentos(totalReservadas);
        if (Boolean.getBoolean("actualizarDatosDemostracion")) {
            String actual = Files.readString(DOCUMENTO, StandardCharsets.UTF_8);
            Files.writeString(DOCUMENTO, actual.substring(0, actual.indexOf(INICIO) + INICIO.length()) + "\n"
                    + calculado + actual.substring(actual.indexOf(FIN)), StandardCharsets.UTF_8);
        }
        String documento = Files.readString(DOCUMENTO, StandardCharsets.UTF_8);
        String documentado = documento.substring(documento.indexOf(INICIO) + INICIO.length(), documento.indexOf(FIN)).strip();
        assertThat(documentado).as("datos-demostracion.md no coincide con la ejecución").isEqualTo(calculado.strip());
        assertThat(documento).doesNotContainPattern("\\b20\\d\\d-\\d\\d-\\d\\d\\b");
    }

    private String recuentos(int totalReservadas) {
        StringBuilder md = new StringBuilder("### Recordatorios por día laborable de la cita\n\n");
        md.append("| Día laborable | Citas reservadas | Recordatorios generados |\n|---|---:|---:|\n");
        for (int posicion = 1; posicion <= 10; posicion++) {
            LocalDate fecha = DiasLaborables.fecha(HOY, posicion);
            Date dia = Date.valueOf(fecha);
            int citas = jdbc.queryForObject("SELECT count(*) FROM cita WHERE estado = 'RESERVADA' "
                    + "AND (inicio AT TIME ZONE 'Europe/Madrid')::date = ?", Integer.class, dia);
            int recordatorios = jdbc.queryForObject("SELECT count(*) FROM recordatorio r JOIN cita c ON c.id = r.cita_id "
                    + "WHERE (c.inicio AT TIME ZONE 'Europe/Madrid')::date = ?", Integer.class, dia);
            md.append("| +").append(posicion).append(" | ").append(citas).append(" | ").append(recordatorios).append(" |\n");
        }
        int recordatorios = jdbc.queryForObject("SELECT count(*) FROM recordatorio", Integer.class);
        md.append("| Total | ").append(totalReservadas).append(" | ").append(recordatorios).append(" |\n");
        int conMenosDe24h = jdbc.queryForObject("SELECT count(*) FROM recordatorio r JOIN cita c ON c.id = r.cita_id "
                + "WHERE c.inicio - r.generado_en < interval '24 hours'", Integer.class);
        md.append("\nRecordatorios con menos de 24 h de antelación: ").append(conMenosDe24h).append(".\n");
        return md.toString();
    }
}
