package es.citaclara.cita;

import es.citaclara.soporte.ClienteApi;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.HOY;
import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;

/** Reloj: miércoles 25/11/2026 a las 12:00 (Madrid). MANANA es jueves 26/11/2026. */
class AltaCitaTest extends PruebaIntegracionBase {

    private static final String HORA_PASADA = "No se pueden crear citas en el pasado. Elige una hora posterior.";
    private static final String FUERA_DE_HORARIO =
            "La cita debe empezar y terminar dentro del horario de atención (09:00 a 20:00).";

    private Escenario e;
    private ClienteApi recepcion;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    @Test
    @DisplayName("[001][FR-009][FR-011][FR-017][US2-AC1] se registra una cita reservada con fin calculado e importe del servicio")
    void altaEnHuecoLibre() {
        Respuesta r = reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00");

        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        JsonNode cita = r.cuerpo();
        assertThat(cita.path("estado").asString()).isEqualTo("RESERVADA");
        assertThat(cita.path("horaInicio").asString()).isEqualTo("10:00");
        assertThat(cita.path("horaFin").asString()).isEqualTo("10:45");
        assertThat(cita.path("importeCentimos").asInt()).isEqualTo(4000);
        assertThat(cita.path("paciente").path("nombre").asString()).isEqualTo("Ana Pérez");

        JsonNode agenda = recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + MANANA).cuerpo();
        assertThat(agenda.path("citas")).hasSize(1);
        assertThat(agenda.path("citas").get(0).path("id").asLong()).isEqualTo(cita.path("id").asLong());

        // El importe es una copia: no cambia aunque después cambie el precio del servicio.
        jdbc.update("UPDATE servicio SET precio_centimos = 9900 WHERE id = ?", e.sesionFisio());
        JsonNode despues = recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + MANANA).cuerpo();
        assertThat(despues.path("citas").get(0).path("importeCentimos").asInt()).isEqualTo(4000);
    }

    @Test
    @DisplayName("[001][FR-012][FR-016][US2-AC2] una cita que pisa otra se rechaza explicando con qué cita choca")
    void solapeParcial() {
        reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00");

        Respuesta r = reservar(e.jorge(), e.sesionFisio(), e.pedro(), MANANA, "10:30");

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("SOLAPE");
        assertThat(r.mensaje()).isEqualTo("Jorge ya tiene una cita de 10:00 a 10:45. Elige otro hueco.");
        assertThat(r.cuerpo().path("citaConflictiva").path("horaInicio").asString()).isEqualTo("10:00");
        assertThat(citas()).isEqualTo(1);
    }

    @Test
    @DisplayName("[001][FR-012] contención y coincidencia exacta también son solape")
    void contencionYCoincidencia() {
        reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00");
        assertThat(reservar(e.jorge(), e.primeraFisio(), e.pedro(), MANANA, "09:45").codigo()).isEqualTo("SOLAPE");
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.pedro(), MANANA, "10:00").codigo()).isEqualTo("SOLAPE");
        assertThat(citas()).isEqualTo(1);
    }

    @Test
    @DisplayName("[001][FR-012][US2-AC3] una cita que empieza justo al terminar otra se registra")
    void contigua() {
        reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00");
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.pedro(), MANANA, "10:45").estado()).isEqualTo(201);
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.pedro(), MANANA, "09:15").estado()).isEqualTo(201);
    }

    @Test
    @DisplayName("[001][FR-024][US2-AC4] las citas canceladas y no asistidas no ocupan el hueco")
    void sobreCanceladaYNoAsistida() {
        datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "CANCELADA");
        datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00", 45, 4000, "NO_ASISTIDA");
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.pedro(), MANANA, "10:00").estado()).isEqualTo(201);
    }

    @Test
    @DisplayName("[001][FR-012][US2-AC6] el solape solo se controla por profesional")
    void otroProfesional() {
        reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:00");
        assertThat(reservar(e.maria(), e.sesionFisio(), e.ana(), MANANA, "10:00").estado()).isEqualTo(201);
    }

    @Test
    @DisplayName("[001][FR-014][SC-003][US2-AC7] no se pueden crear citas en el pasado ni que empiecen ahora")
    void pasado() {
        for (Respuesta r : new Respuesta[]{
                reservar(e.jorge(), e.sesionFisio(), e.ana(), HOY, "11:30"),
                reservar(e.jorge(), e.sesionFisio(), e.ana(), HOY, "12:00"),
                reservar(e.jorge(), e.sesionFisio(), e.ana(), HOY.minusDays(1), "15:00")}) {
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.codigo()).isEqualTo("HORA_PASADA");
            assertThat(r.mensaje()).isEqualTo(HORA_PASADA);
        }
        reloj.avanzar(Duration.ofSeconds(30));
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.ana(), HOY, "12:00").codigo()).isEqualTo("HORA_PASADA");
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.ana(), HOY, "12:05").estado()).isEqualTo(201);
        assertThat(citas()).isEqualTo(1);
    }

    @Test
    @DisplayName("[001][FR-010][FR-027][US2-AC9] solo se reservan servicios de la especialidad del profesional")
    void servicioDeOtraEspecialidad() {
        Respuesta jorge = reservar(e.jorge(), e.consultaNutri(), e.ana(), MANANA, "10:00");
        assertThat(jorge.estado()).isEqualTo(422);
        assertThat(jorge.codigo()).isEqualTo("SERVICIO_NO_DISPONIBLE");
        assertThat(jorge.mensaje()).isEqualTo("Jorge no ofrece ese servicio. Elige uno de fisioterapia.");

        Respuesta lucia = reservar(e.lucia(), e.sesionFisio(), e.ana(), MANANA, "10:00");
        assertThat(lucia.mensaje()).isEqualTo("Lucía no ofrece ese servicio. Elige uno de nutrición.");
        assertThat(citas()).isZero();
    }

    @Test
    @DisplayName("[001][FR-015][US2-AC10] una cita que termina o empieza fuera del horario se rechaza indicándolo")
    void fueraDeHorario() {
        Respuesta tarde = reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "19:30");
        assertThat(tarde.estado()).isEqualTo(422);
        assertThat(tarde.codigo()).isEqualTo("FUERA_DE_HORARIO");
        assertThat(tarde.mensaje()).isEqualTo(FUERA_DE_HORARIO);
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "08:55").mensaje()).isEqualTo(FUERA_DE_HORARIO);
        assertThat(reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "19:15").estado()).isEqualTo(201);

        Respuesta sabado = reservar(e.jorge(), e.sesionFisio(), e.ana(), LocalDate.of(2026, 11, 28), "10:00");
        assertThat(sabado.codigo()).isEqualTo("FUERA_DE_HORARIO");
        assertThat(sabado.mensaje()).isEqualTo("La clínica no atiende ese día.");
    }

    @Test
    @DisplayName("[001][FR-009] la hora de inicio va en pasos de 5 minutos")
    void horaNoMultiploDeCinco() {
        Respuesta r = reservar(e.jorge(), e.sesionFisio(), e.ana(), MANANA, "10:03");
        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.codigo()).isEqualTo("HORA_NO_VALIDA");
        assertThat(r.mensaje()).doesNotContain("HORA_NO_VALIDA");
    }

    @Test
    @DisplayName("[001][FR-003] no se puede reservar con profesional, servicio o paciente de otra clínica")
    void recursosDeOtraClinica() {
        OtraClinica otra = datos.otraClinica();
        assertThat(reservar(otra.profesional(), e.sesionFisio(), e.ana(), MANANA, "10:00").codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(reservar(e.jorge(), otra.servicio(), e.ana(), MANANA, "10:00").codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(reservar(e.jorge(), e.sesionFisio(), otra.paciente(), MANANA, "10:00").codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(citas()).isZero();
    }

    private Respuesta reservar(long profesional, long servicio, long paciente, LocalDate fecha, String hora) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("profesionalId", profesional);
        cuerpo.put("servicioId", servicio);
        cuerpo.put("pacienteId", paciente);
        cuerpo.put("fecha", fecha.toString());
        cuerpo.put("horaInicio", hora);
        return recepcion.post("/api/citas", cuerpo);
    }

    private int citas() {
        return jdbc.queryForObject("SELECT count(*) FROM cita", Integer.class);
    }
}
