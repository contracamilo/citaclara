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

/** Spec 005, FR-009 (H-12): longitud máxima de la búsqueda y comodines como texto literal. */
class LimitesEntradaTest extends PruebaIntegracionBase {

    private ClienteApi sesion;
    private Escenario eleva;

    @BeforeEach
    void preparar() {
        eleva = datos.escenario();
        sesion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    private static String q(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("[005][FR-009] una búsqueda de 80 caracteres se acepta y una de 81 se rechaza con un mensaje para la persona usuaria")
    void longitudMaxima() {
        assertThat(sesion.get("/api/pacientes?q=" + q("a".repeat(80))).estado()).isEqualTo(200);
        Respuesta larga = sesion.get("/api/pacientes?q=" + q("a".repeat(81)));
        assertThat(larga.estado()).isEqualTo(400);
        assertThat(larga.cuerpo().path("mensaje").asString("")).contains("80 caracteres");
    }

    @Test
    @DisplayName("[005][FR-009] «%», «_» y «\\» se buscan como texto literal y no como comodines")
    void comodinesLiterales() {
        Respuesta alta = sesion.post("/api/citas", Map.of(
                "profesionalId", eleva.jorge(), "servicioId", eleva.sesionFisio(),
                "nuevoPaciente", Map.of("nombre", "Ana 100%_real", "telefono", "699445566"),
                "fecha", DatosPrueba.MANANA.toString(), "horaInicio", "17:00"));
        assertThat(alta.estado()).isEqualTo(201);

        assertThat(sesion.get("/api/pacientes?q=" + q("%%")).cuerpo().size()).isZero();
        assertThat(sesion.get("/api/pacientes?q=" + q("__")).cuerpo().size()).isZero();
        assertThat(sesion.get("/api/pacientes?q=" + q("\\\\")).cuerpo().size()).isZero();
        Respuesta exacta = sesion.get("/api/pacientes?q=" + q("100%_"));
        assertThat(exacta.cuerpo().size()).isEqualTo(1);
        assertThat(exacta.cuerpo().get(0).path("nombre").asString("")).isEqualTo("Ana 100%_real");
    }
}
