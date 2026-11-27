package es.citaclara.acceso;

import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static es.citaclara.soporte.DatosPrueba.AHORA_POR_DEFECTO;
import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.CLAVE_OTRA;
import static org.assertj.core.api.Assertions.assertThat;

class ContextoSesionTest extends PruebaIntegracionBase {

    @Test
    @DisplayName("[001][FR-003] el contexto de la sesión trae solo los datos de su clínica")
    void contextoDeLaClinica() {
        datos.escenario();
        datos.otraClinica();

        Respuesta r = clienteConSesion("eleva", CLAVE_ELEVA).get("/api/sesion");

        assertThat(r.estado()).isEqualTo(200);
        JsonNode c = r.cuerpo();
        assertThat(c.path("clinica").path("nombre").asString()).isEqualTo("Clínica Eleva");
        assertThat(c.path("hoy").asString()).isEqualTo("2026-11-25");
        assertThat(Instant.parse(c.path("sesionCaducaA").asString()))
                .isEqualTo(AHORA_POR_DEFECTO.toInstant().plusSeconds(8 * 3600));
        assertThat(nombres(c.path("profesionales"))).containsExactlyInAnyOrder("María", "Jorge", "Lucía");
        assertThat(nombres(c.path("servicios"))).hasSize(4).doesNotContain("Clínica Otra");
        assertThat(r.texto()).doesNotContain("Elena", "Clínica Otra", "Lucía Martín");

        JsonNode sesion = buscar(c.path("servicios"), "Sesión fisioterapia");
        assertThat(sesion.path("duracionMinutos").isInt()).isTrue();
        assertThat(sesion.path("duracionMinutos").asInt()).isEqualTo(45);
        assertThat(sesion.path("precioCentimos").isInt()).isTrue();
        assertThat(sesion.path("precioCentimos").asInt()).isEqualTo(4000);

        assertThat(c.path("horario")).hasSize(5);
        assertThat(c.path("horario").get(0).path("diaSemana").asInt()).isEqualTo(1);
        assertThat(c.path("horario").get(0).path("apertura").asString()).isEqualTo("09:00");
        assertThat(c.path("horario").get(0).path("cierre").asString()).isEqualTo("20:00");
    }

    @Test
    @DisplayName("[001][FR-027] la especialidad visible va en español de España con tilde")
    void especialidadVisible() {
        datos.escenario();
        JsonNode c = clienteConSesion("eleva", CLAVE_ELEVA).get("/api/sesion").cuerpo();
        JsonNode lucia = buscar(c.path("profesionales"), "Lucía");
        assertThat(lucia.path("especialidad").asString()).isEqualTo("nutricion");
        assertThat(lucia.path("especialidadNombre").asString()).isEqualTo("Nutrición");
        assertThat(buscar(c.path("servicios"), "Consulta nutrición").path("especialidadNombre").asString())
                .isEqualTo("Nutrición");
        assertThat(buscar(c.path("profesionales"), "Jorge").path("especialidadNombre").asString())
                .isEqualTo("Fisioterapia");
    }

    @Test
    @DisplayName("[001][FR-003] con la clave de otra clínica solo se ve esa otra clínica")
    void otraClinica() {
        datos.escenario();
        datos.otraClinica();
        Respuesta r = clienteConSesion("otra", CLAVE_OTRA).get("/api/sesion");
        assertThat(nombres(r.cuerpo().path("profesionales"))).containsExactly("Elena");
        assertThat(r.texto()).doesNotContain("María", "Jorge", "Clínica Eleva");
    }

    private static List<String> nombres(JsonNode lista) {
        List<String> nombres = new ArrayList<>();
        lista.forEach(n -> nombres.add(n.path("nombre").asString()));
        return nombres;
    }

    private static JsonNode buscar(JsonNode lista, String nombre) {
        for (JsonNode n : lista) {
            if (nombre.equals(n.path("nombre").asString())) {
                return n;
            }
        }
        throw new AssertionError("No está " + nombre);
    }
}
