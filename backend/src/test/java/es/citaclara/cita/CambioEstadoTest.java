package es.citaclara.cita;

import es.citaclara.soporte.Agendas;
import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.DatosPrueba.OtraClinica;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;

/** Reloj: miércoles 25/11/2026 a las 12:00. La cita de Jorge es el jueves 26/11 de 10:00 a 10:45. */
class CambioEstadoTest extends PruebaIntegracionBase {

    private Escenario e;
    private ClienteApi recepcion;
    private long cita;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
        cita = datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "RESERVADA");
    }

    @Test
    @DisplayName("[001][FR-022][US3-AC1] una cita reservada futura se cancela y su franja queda libre")
    void cancelarFutura() {
        Respuesta r = cambiar(cita, "CANCELAR", 0);

        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        assertThat(r.cuerpo().path("estado").asString()).isEqualTo("CANCELADA");
        assertThat(r.cuerpo().path("acciones")).isEmpty();
        assertThat(r.cuerpo().path("version").asInt()).isEqualTo(1);
        assertThat(Agendas.huecos(agenda())).containsExactly("09:00-20:00");
    }

    @Test
    @DisplayName("[001][FR-023][FR-024][US3-AC2] desde el inicio se marca completada y sigue ocupando su franja")
    void completarDesdeElInicio() {
        empiezaLaCita();
        Respuesta r = cambiar(cita, "COMPLETAR", 0);

        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        assertThat(r.cuerpo().path("estado").asString()).isEqualTo("COMPLETADA");
        assertThat(Agendas.huecos(agenda())).containsExactly("09:00-10:00", "10:45-20:00");
        Respuesta solape = recepcion.post("/api/citas", Map.of("profesionalId", e.jorge(),
                "servicioId", e.sesionFisio(), "pacienteId", e.pedro(), "fecha", MANANA.toString(), "horaInicio", "10:30"));
        assertThat(solape.codigo()).isEqualTo("SOLAPE");
    }

    @Test
    @DisplayName("[001][FR-023][US3-AC3] desde el inicio se marca no asistida")
    void noAsistida() {
        empiezaLaCita();
        reloj.avanzar(Duration.ofMinutes(20));
        Respuesta r = cambiar(cita, "MARCAR_NO_ASISTIDA", 0);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().path("estado").asString()).isEqualTo("NO_ASISTIDA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETAR", "MARCAR_NO_ASISTIDA"})
    @DisplayName("[001][FR-023][US3-AC4] antes del inicio no se puede completar ni marcar no asistida")
    void antesDelInicio(String accion) {
        Respuesta r = cambiar(cita, accion, 0);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.codigo()).isEqualTo("ACCION_NO_DISPONIBLE");
        assertThat(r.mensaje()).isEqualTo("Esta cita aún no ha empezado: solo se puede cancelar.");
        assertThat(estadoEnBd()).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[001][FR-022][US3-AC5] una cita ya empezada no se cancela: solo completada o no asistida")
    void noCancelarEmpezada() {
        empiezaLaCita();
        assertThat(agenda().path("citas").get(0).path("acciones")).extracting(JsonNode::asString)
                .containsExactly("COMPLETAR", "MARCAR_NO_ASISTIDA");
        Respuesta r = cambiar(cita, "CANCELAR", 0);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.mensaje()).isEqualTo(
                "Esta cita ya ha empezado: no se puede cancelar. Márcala como completada o no asistida.");
        assertThat(estadoEnBd()).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[001][FR-021][US3-AC6] una cita cerrada no ofrece ni admite cambios de estado")
    void cerrada() {
        assertThat(cambiar(cita, "CANCELAR", 0).estado()).isEqualTo(200);
        empiezaLaCita();
        for (String accion : new String[]{"CANCELAR", "COMPLETAR", "MARCAR_NO_ASISTIDA"}) {
            Respuesta r = cambiar(cita, accion, 1);
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.mensaje()).isEqualTo("Esta cita ya está cerrada y no admite cambios.");
        }
        assertThat(agenda().path("citas").get(0).path("acciones")).isEmpty();
    }

    @Test
    @DisplayName("[001][FR-003] la cita de otra clínica no existe para esta sesión")
    void otraClinica() {
        OtraClinica otra = datos.otraClinica();
        long ajena = datos.cita(otra.clinica(), otra.profesional(), otra.servicio(), otra.paciente(),
                DatosPrueba.instante(MANANA, "10:00"), DatosPrueba.instante(MANANA, "10:45"), 4000, "RESERVADA");
        Respuesta r = cambiar(ajena, "CANCELAR", 0);
        assertThat(r.estado()).isEqualTo(404);
        assertThat(r.codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, ajena)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[001][FR-002] con la sesión caducada el cambio de estado no se aplica")
    void sesionCaducada() {
        reloj.avanzar(Duration.ofHours(8));
        Respuesta r = cambiar(cita, "CANCELAR", 0);
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SESION_CADUCADA");
        assertThat(estadoEnBd()).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[001][FR-021] una acción desconocida se rechaza sin jerga")
    void accionDesconocida() {
        Respuesta r = cambiar(cita, "REACTIVAR", 0);
        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.codigo()).isEqualTo("DATOS_NO_VALIDOS");
    }

    /** Lleva el reloj al inicio de la cita (22 h después) y vuelve a entrar: la sesión anterior caduca a las 8 h. */
    private void empiezaLaCita() {
        reloj.fijar(MANANA.atTime(LocalTime.of(10, 0)).atZone(DatosPrueba.MADRID));
        recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    private Respuesta cambiar(long citaId, String accion, int version) {
        return recepcion.post("/api/citas/" + citaId + "/estado", Map.of("accion", accion, "version", version));
    }

    private JsonNode agenda() {
        return recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + MANANA).cuerpo();
    }

    private String estadoEnBd() {
        return jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, cita);
    }
}
