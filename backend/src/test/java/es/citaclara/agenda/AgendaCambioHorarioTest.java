package es.citaclara.agenda;

import es.citaclara.soporte.Agendas;
import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;

/** Días de cambio de hora (domingos): clínica de prueba que también atiende en domingo de 00:00 a 23:00. */
class AgendaCambioHorarioTest extends PruebaIntegracionBase {

    private Escenario e;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        datos.horario(e.clinica(), 7, LocalTime.of(0, 0), LocalTime.of(23, 0));
        reloj.fijar(ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, DatosPrueba.MADRID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-25", "2027-03-28"})
    @DisplayName("[001][FR-026] en el día del cambio de hora la cita conserva su hora local y su duración, una sola vez")
    void citaEnCambioDeHora(String dia) {
        LocalDate fecha = LocalDate.parse(dia);
        datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), fecha, "10:00", 45, 4000, "RESERVADA");
        ClienteApi recepcion = clienteConSesion("eleva", CLAVE_ELEVA);

        JsonNode agenda = recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + fecha).cuerpo();

        assertThat(agenda.path("citas")).hasSize(1);
        assertThat(agenda.path("citas").get(0).path("horaInicio").asString()).isEqualTo("10:00");
        assertThat(agenda.path("citas").get(0).path("horaFin").asString()).isEqualTo("10:45");
        assertThat(Agendas.huecos(agenda)).containsExactly("00:00-10:00", "10:45-23:00");
        for (LocalDate vecino : new LocalDate[]{fecha.minusDays(1), fecha.plusDays(1)}) {
            JsonNode otra = recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + vecino).cuerpo();
            assertThat(otra.path("citas")).isEmpty();
        }

        // Duración real del horario de ese día: 23 h normales, 24 h en octubre (hora repetida), 22 h en marzo.
        Duration esperada = Duration.between(
                fecha.atStartOfDay(DatosPrueba.MADRID), fecha.atTime(23, 0).atZone(DatosPrueba.MADRID));
        Duration libre = Duration.ofMinutes(0);
        for (JsonNode hueco : agenda.path("huecos")) {
            libre = libre.plus(Duration.between(
                    fecha.atTime(LocalTime.parse(hueco.path("horaInicio").asString())).atZone(DatosPrueba.MADRID),
                    fecha.atTime(LocalTime.parse(hueco.path("horaFin").asString())).atZone(DatosPrueba.MADRID)));
        }
        assertThat(libre.plusMinutes(45)).isEqualTo(esperada);
        assertThat(esperada).isNotEqualTo(Duration.ofHours(23));
    }
}
