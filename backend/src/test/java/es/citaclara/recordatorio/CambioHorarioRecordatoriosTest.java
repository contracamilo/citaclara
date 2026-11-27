package es.citaclara.recordatorio;

import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cambios de horario de 2027: 28/03 (día de 23 h) y 31/10 (día de 25 h). Principio II. */
class CambioHorarioRecordatoriosTest extends PruebaRecordatoriosBase {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
    }

    @Test
    @DisplayName("[003][SC-005][US1-AC1] alrededor del cambio de marzo de 2027 ninguna cita se omite ni se duplica y la hora del correo es la peninsular")
    void cambioDeMarzo() {
        comprobarEjecucionesDiarias(LocalDate.of(2027, 3, 26), 5, LocalDate.of(2027, 3, 27));
    }

    @Test
    @DisplayName("[003][SC-005][US1-AC1] alrededor del cambio de octubre de 2027 ninguna cita se omite ni se duplica y la hora del correo es la peninsular")
    void cambioDeOctubre() {
        comprobarEjecucionesDiarias(LocalDate.of(2027, 10, 29), 5, LocalDate.of(2027, 10, 30));
    }

    /**
     * Ejecuta el proceso a las 09:00 locales durante {@code dias} días y reparte una cita cada hora
     * en todo el intervalo cubierto. Cada cita debe generarse exactamente una vez y con ≥ 24 h de antelación.
     */
    private void comprobarEjecucionesDiarias(LocalDate primerDia, int dias, LocalDate primerDiaConCitas) {
        ZonedDateTime primeraEjecucion = ZonedDateTime.of(primerDia, LocalTime.of(9, 0), MADRID);
        Instant desde = primeraEjecucion.plusDays(1).toInstant().plus(Duration.ofHours(24)).minus(Duration.ofHours(24));
        Instant hasta = primeraEjecucion.plusDays(dias).toInstant().plus(Duration.ofHours(24));
        List<Long> citas = new ArrayList<>();
        for (Instant inicio = desde; inicio.isBefore(hasta); inicio = inicio.plus(Duration.ofHours(1))) {
            citas.add(datos.cita(e.clinica(), e.maria(), e.sesionFisio(), e.ana(), inicio,
                    inicio.plusSeconds(45 * 60L), 4000, "RESERVADA"));
        }

        int generados = 0;
        for (int i = 0; i < dias; i++) {
            reloj.fijar(primeraEjecucion.plusDays(i)); // misma hora local cada día, aunque el día tenga 23 o 25 h
            generados += servicio.generar();
        }

        assertThat(generados).as("cada cita, exactamente una vez").isEqualTo(citas.size());
        assertThat(ficherosEml()).hasSize(citas.size());
        // Antelación: nunca menos de 24 h entre la generación y el inicio de la cita.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM recordatorio r JOIN cita c ON c.id = r.cita_id
                WHERE c.inicio - r.generado_en < interval '24 hours'
                """, Integer.class)).isZero();
        // La hora que ve el paciente es la hora local de Madrid de la cita.
        for (long cita : citas) {
            ZonedDateTime local = jdbc.queryForObject("SELECT inicio FROM cita WHERE id = ?", java.sql.Timestamp.class, cita)
                    .toInstant().atZone(MADRID);
            assertThat(cuerpoEml(cita)).contains("Hora:        " + String.format("%02d:%02d", local.getHour(), local.getMinute()));
        }
    }
}
