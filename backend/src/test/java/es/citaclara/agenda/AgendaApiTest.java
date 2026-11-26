package es.citaclara.agenda;

import es.citaclara.soporte.ClienteApi;
import static es.citaclara.soporte.Agendas.huecos;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.DatosPrueba.OtraClinica;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.HOY;
import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;

class AgendaApiTest extends PruebaIntegracionBase {

    private Escenario e;
    private ClienteApi recepcion;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    @Test
    @DisplayName("[001][FR-001][US1-AC1] la agenda no se ve sin sesión")
    void sinSesion() {
        assertThat(cliente().get("/api/agenda?profesionalId=" + e.maria()).estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("[001][FR-005][US1-AC3] sin fecha se muestra la agenda de hoy según el servidor")
    void hoyPorDefecto() {
        JsonNode agenda = recepcion.get("/api/agenda?profesionalId=" + e.maria()).cuerpo();
        assertThat(agenda.path("fecha").asString()).isEqualTo(HOY.toString());
        assertThat(agenda.path("hoy").asString()).isEqualTo(HOY.toString());
        assertThat(agenda.path("profesional").path("nombre").asString()).isEqualTo("María");
        assertThat(agenda.path("profesional").path("especialidadNombre").asString()).isEqualTo("Fisioterapia");

        JsonNode otroDia = recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=2026-12-01").cuerpo();
        assertThat(otroDia.path("fecha").asString()).isEqualTo("2026-12-01");
        assertThat(otroDia.path("hoy").asString()).isEqualTo(HOY.toString());
        assertThat(otroDia.path("profesional").path("nombre").asString()).isEqualTo("Jorge");
    }

    @Test
    @DisplayName("[001][FR-006][US1-AC4] cada cita trae horario de 24 h, paciente, servicio, importe y estado, en orden")
    void datosDeCadaCita() {
        datos.cita(e, e.maria(), e.primeraFisio(), e.pedro(), MANANA, "16:00", 60, 5000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "RESERVADA");

        JsonNode agenda = agenda(e.maria(), MANANA);
        JsonNode citas = agenda.path("citas");
        assertThat(citas).hasSize(2);
        JsonNode primera = citas.get(0);
        assertThat(primera.path("horaInicio").asString()).isEqualTo("10:00");
        assertThat(primera.path("horaFin").asString()).isEqualTo("10:45");
        assertThat(primera.path("fecha").asString()).isEqualTo(MANANA.toString());
        assertThat(primera.path("paciente").path("nombre").asString()).isEqualTo("Ana Pérez");
        assertThat(primera.path("servicio").path("nombre").asString()).isEqualTo("Sesión fisioterapia");
        assertThat(primera.path("importeCentimos").asInt()).isEqualTo(4000);
        assertThat(primera.path("estado").asString()).isEqualTo("RESERVADA");
        assertThat(primera.path("version").isInt()).isTrue();
        assertThat(primera.path("acciones")).extracting(JsonNode::asString).containsExactly("CANCELAR");
        assertThat(citas.get(1).path("horaInicio").asString()).isEqualTo("16:00");
        assertThat(citas.get(1).path("horaFin").asString()).isEqualTo("17:00");

        assertThat(huecos(agenda)).containsExactly("09:00-10:00", "10:45-16:00", "17:00-20:00");
        assertThat(agenda.path("horario").path("apertura").asString()).isEqualTo("09:00");
        assertThat(agenda.path("horario").path("cierre").asString()).isEqualTo("20:00");
    }

    @Test
    @DisplayName("[001][FR-024][US1-AC5] las canceladas y no asistidas se ven con su estado pero su franja está libre")
    void canceladasVisiblesPeroLibres() {
        datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "CANCELADA");
        datos.cita(e, e.jorge(), e.sesionFisio(), e.pedro(), MANANA, "12:00", 45, 4000, "NO_ASISTIDA");
        datos.cita(e, e.jorge(), e.sesionFisio(), e.pedro(), MANANA, "15:00", 45, 4000, "COMPLETADA");

        JsonNode agenda = agenda(e.jorge(), MANANA);
        assertThat(agenda.path("citas")).extracting(c -> c.path("estado").asString())
                .containsExactly("CANCELADA", "NO_ASISTIDA", "COMPLETADA");
        assertThat(agenda.path("citas")).allSatisfy(c -> assertThat(c.path("acciones")).isEmpty());
        assertThat(huecos(agenda)).containsExactly("09:00-15:00", "15:45-20:00");
    }

    @Test
    @DisplayName("[001][FR-007][US1-AC6] un día sin citas muestra todo el horario como libre")
    void diaSinCitas() {
        JsonNode agenda = agenda(e.lucia(), MANANA);
        assertThat(agenda.path("citas")).isEmpty();
        assertThat(huecos(agenda)).containsExactly("09:00-20:00");
        assertThat(agenda.path("huecos").get(0).path("reservable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("[001][FR-015] un sábado la clínica no atiende: sin horario ni huecos")
    void sabado() {
        JsonNode agenda = agenda(e.maria(), LocalDate.of(2026, 11, 28));
        assertThat(agenda.path("horario").isNull()).isTrue();
        assertThat(agenda.path("huecos")).isEmpty();
    }

    @Test
    @DisplayName("[001][FR-008][FR-022][FR-023] las acciones y la reservabilidad las decide el reloj del servidor")
    void accionesSegunReloj() {
        datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), HOY, "11:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), e.pedro(), HOY, "13:00", 45, 4000, "RESERVADA");

        JsonNode agenda = agenda(e.maria(), HOY);
        assertThat(agenda.path("citas").get(0).path("acciones")).extracting(JsonNode::asString)
                .containsExactly("COMPLETAR", "MARCAR_NO_ASISTIDA");
        assertThat(agenda.path("citas").get(1).path("acciones")).extracting(JsonNode::asString)
                .containsExactly("CANCELAR");
        assertThat(agenda.path("huecos").get(0).path("reservable").asBoolean()).isFalse();
        assertThat(agenda.path("huecos").get(1).path("reservable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("[001][FR-003] el profesional de otra clínica no existe para esta sesión")
    void otraClinica() {
        OtraClinica otra = datos.otraClinica();
        Respuesta r = recepcion.get("/api/agenda?profesionalId=" + otra.profesional());
        assertThat(r.estado()).isEqualTo(404);
        assertThat(r.codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(r.texto()).doesNotContain("Elena");
    }

    @Test
    @DisplayName("[001][FR-005] una fecha mal escrita se rechaza sin jerga")
    void fechaMalFormada() {
        Respuesta r = recepcion.get("/api/agenda?profesionalId=" + e.maria() + "&fecha=26-11-2026");
        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.codigo()).isEqualTo("DATOS_NO_VALIDOS");
    }

    @Test
    @DisplayName("[001][SC-007] la agenda de un día con 20 citas responde en menos de 500 ms")
    void rendimiento() {
        for (int i = 0; i < 20; i++) {
            String hora = String.format("%02d:%02d", 9 + (i * 30) / 60, (i * 30) % 60);
            datos.cita(e, e.maria(), e.consultaNutri(), e.ana(), MANANA, hora, 30, 3500, "RESERVADA");
        }
        agenda(e.maria(), MANANA); // calentamiento
        long inicio = System.nanoTime();
        JsonNode agenda = agenda(e.maria(), MANANA);
        Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);
        assertThat(agenda.path("citas")).hasSize(20);
        assertThat(duracion).isLessThan(Duration.ofMillis(500));
    }

    private JsonNode agenda(long profesionalId, LocalDate fecha) {
        Respuesta r = recepcion.get("/api/agenda?profesionalId=" + profesionalId + "&fecha=" + fecha);
        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        return r.cuerpo();
    }

}
