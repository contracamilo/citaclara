package es.citaclara.seguridad;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-005: metacaracteres SQL, HTML y Unicode se tratan siempre como texto. */
class EntradasMaliciosasTest extends PruebaIntegracionBase {

    private static final String CARGA = "<script>alert(1)</script> '; DROP TABLE paciente;--";

    private Escenario eleva;
    private ClienteApi sesion;

    @BeforeEach
    void preparar() {
        eleva = datos.escenario();
        sesion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("[005][FR-005] un nombre con HTML y SQL se guarda y se devuelve literalmente, como JSON, sin dañar la base")
    void nombreMalicioso() {
        Respuesta alta = sesion.post("/api/citas", Map.of(
                "profesionalId", eleva.jorge(), "servicioId", eleva.sesionFisio(),
                "nuevoPaciente", Map.of("nombre", CARGA, "telefono", "699112233"),
                "fecha", DatosPrueba.MANANA.toString(), "horaInicio", "16:00"));
        assertThat(alta.estado()).isEqualTo(201);

        Respuesta busqueda = sesion.get("/api/pacientes?q=" + codificar("DROP TABLE"));
        assertThat(busqueda.estado()).isEqualTo(200);
        assertThat(busqueda.tipoContenido()).startsWith("application/json");
        assertThat(busqueda.cuerpo().get(0).path("nombre").asString("")).isEqualTo(CARGA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente", Integer.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("[005][FR-005] una inyección clásica en la búsqueda no devuelve filas de más ni provoca error")
    void inyeccionEnBusqueda() {
        for (String q : new String[]{"' OR '1'='1", "'; SELECT pg_sleep(5);--", "\" OR \"\"=\"", "ana' UNION SELECT * FROM clinica--"}) {
            Respuesta r = sesion.get("/api/pacientes?q=" + codificar(q));
            assertThat(r.estado()).as(q).isEqualTo(200);
            assertThat(r.cuerpo().size()).as(q).isZero();
            assertThat(r.texto()).as(q).doesNotContain("clave_hash", "Exception");
        }
    }

    @Test
    @DisplayName("[005][FR-005] el identificador de clínica con metacaracteres no cambia el resultado ni da error interno")
    void identificadorMalicioso() throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        for (String id : new String[]{"eleva%27--", "eleva%27%20OR%201=1--", "..%2F..%2Fetc%2Fpasswd", "%00", "%25%32%65"}) {
            java.net.http.HttpRequest peticion = java.net.http.HttpRequest
                    .newBuilder(java.net.URI.create("http://localhost:" + puerto + "/api/clinicas/" + id + "/sesion"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"clave\":\"mala\"}")).build();
            java.net.http.HttpResponse<String> r = http.send(peticion, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).as(id).isIn(400, 401, 404, 405);
            assertThat(r.body()).as(id).doesNotContain("Exception", "org.springframework", "Apache Tomcat/", "at es.citaclara");
        }
    }

    @Test
    @DisplayName("[005][FR-005] Unicode raro, emojis y longitudes excesivas se rechazan o se guardan sin error interno")
    void unicodeYLongitudes() {
        String largo = "A".repeat(500);
        Respuesta demasiado = sesion.post("/api/citas", Map.of(
                "profesionalId", eleva.jorge(), "servicioId", eleva.sesionFisio(),
                "nuevoPaciente", Map.of("nombre", largo, "telefono", "699112233"),
                "fecha", DatosPrueba.MANANA.toString(), "horaInicio", "17:00"));
        assertThat(demasiado.estado()).isEqualTo(400);
        assertThat(demasiado.codigo()).isEqualTo("DATOS_NO_VALIDOS");

        Respuesta raro = sesion.post("/api/citas", Map.of(
                "profesionalId", eleva.jorge(), "servicioId", eleva.sesionFisio(),
                "nuevoPaciente", Map.of("nombre", "Zoë 😀 ‮oñam", "telefono", "699445566"),
                "fecha", DatosPrueba.MANANA.toString(), "horaInicio", "18:00"));
        assertThat(raro.estado()).isIn(201, 400);
        assertThat(raro.estado()).isNotEqualTo(500);
    }

    @Test
    @DisplayName("[005][FR-005] los errores no exponen trazas, nombres de clases ni consultas")
    void erroresSinDetallesInternos() {
        Respuesta malFormada = sesion.postCrudo("/api/citas", "application/json", "{\"profesionalId\": [[[[[[");
        assertThat(malFormada.estado()).isEqualTo(400);
        assertThat(malFormada.texto()).doesNotContain("Exception", "org.springframework", "at es.citaclara", "SELECT");
        Respuesta tipoMal = sesion.get("/api/agenda?profesionalId=abc");
        assertThat(tipoMal.estado()).isEqualTo(400);
        assertThat(tipoMal.texto()).doesNotContain("Exception", "NumberFormat", "java.lang");
    }
}
