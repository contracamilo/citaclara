package es.citaclara.acceso;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;

class AccesoTest extends PruebaIntegracionBase {

    private static final String CLAVE_INCORRECTA = "La clave no es correcta. Revísala e inténtalo de nuevo.";
    private static final String SESION_CADUCADA = "Tu sesión ha caducado. Vuelve a introducir la clave de la clínica.";

    @BeforeEach
    void preparar() {
        datos.escenario();
    }

    @Test
    @DisplayName("[001][FR-001][US1-AC1] sin sesión no se ve ningún dato del panel")
    void sinSesion() {
        Respuesta r = cliente().get("/api/sesion");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SIN_SESION");
        assertThat(r.tipoContenido()).startsWith("application/problem+json");
        assertThat(r.texto()).doesNotContain("María", "Jorge");
    }

    @Test
    @DisplayName("[001][FR-001][US1-AC2] con la clave incorrecta se rechaza el acceso con un mensaje claro")
    void claveIncorrecta() {
        ClienteApi cliente = cliente();
        Respuesta r = cliente.post("/api/clinicas/eleva/sesion", Map.of("clave", "otra-cosa"));
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("CLAVE_INCORRECTA");
        assertThat(r.mensaje()).isEqualTo(CLAVE_INCORRECTA);
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("[001][FR-004][US1-AC2] una clínica inexistente responde igual que una clave incorrecta")
    void clinicaInexistenteMismaRespuesta() {
        Respuesta r = cliente().post("/api/clinicas/no-existe/sesion", Map.of("clave", CLAVE_ELEVA));
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("CLAVE_INCORRECTA");
        assertThat(r.mensaje()).isEqualTo(CLAVE_INCORRECTA);
    }

    @Test
    @DisplayName("[001][FR-004] sin límite de intentos: tras 10 claves erróneas la correcta sigue entrando")
    void sinLimiteDeIntentos() {
        ClienteApi cliente = cliente();
        for (int i = 0; i < 10; i++) {
            Respuesta r = cliente.post("/api/clinicas/eleva/sesion", Map.of("clave", "mala-" + i));
            assertThat(r.mensaje()).isEqualTo(CLAVE_INCORRECTA);
        }
        assertThat(cliente.post("/api/clinicas/eleva/sesion", Map.of("clave", CLAVE_ELEVA)).estado()).isEqualTo(204);
    }

    @Test
    @DisplayName("[001][FR-001] con la clave correcta se concede el acceso con una cookie HttpOnly y SameSite=Strict")
    void claveCorrecta() {
        ClienteApi cliente = cliente();
        Respuesta r = cliente.post("/api/clinicas/eleva/sesion", Map.of("clave", CLAVE_ELEVA));
        assertThat(r.estado()).isEqualTo(204);
        String cookie = r.cabeceras().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).contains("HttpOnly").containsIgnoringCase("SameSite=Strict");
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(200);
    }

    @Test
    @DisplayName("[001][FR-002] el acceso dura 8 horas desde la clave, con independencia de la actividad")
    void caducidadAbsoluta() {
        ClienteApi cliente = clienteConSesion("eleva", CLAVE_ELEVA);
        for (int i = 0; i < 7; i++) {
            reloj.avanzar(Duration.ofHours(1));
            assertThat(cliente.get("/api/sesion").estado()).isEqualTo(200);
        }
        reloj.avanzar(Duration.ofMinutes(59));
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(200);

        reloj.avanzar(Duration.ofMinutes(1));
        Respuesta r = cliente.get("/api/sesion");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SESION_CADUCADA");
        assertThat(r.mensaje()).isEqualTo(SESION_CADUCADA);
        // La sesión se ha invalidado: aunque el reloj volviera atrás, ya no vale.
        reloj.avanzar(Duration.ofHours(-8));
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("[001][FR-002] cerrar la sesión explícitamente quita el acceso")
    void cerrarSesion() {
        ClienteApi cliente = clienteConSesion("eleva", CLAVE_ELEVA);
        assertThat(cliente.delete("/api/sesion").estado()).isEqualTo(204);
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(401);
        assertThat(cliente.delete("/api/sesion").estado()).isEqualTo(204);
    }

    @Test
    @DisplayName("[001][FR-001] los datos públicos de la clínica son solo su nombre y nunca la clave")
    void datosPublicos() {
        Respuesta r = cliente().get("/api/clinicas/eleva");
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().propertyNames()).containsExactly("nombre");
        assertThat(r.cuerpo().path("nombre").asString()).isEqualTo("Clínica Eleva");
        assertThat(cliente().get("/api/clinicas/no-existe").codigo()).isEqualTo("NO_ENCONTRADO");
        assertThat(clienteConSesion("eleva", CLAVE_ELEVA).get("/api/sesion").texto())
                .doesNotContain("clave", "$2a$");
    }

    @Test
    @DisplayName("[001][FR-001] un POST que no es JSON se rechaza con 415 y no crea sesión")
    void soloJson() {
        ClienteApi cliente = cliente();
        Respuesta r = cliente.postCrudo("/api/clinicas/eleva/sesion",
                "application/x-www-form-urlencoded", "clave=" + CLAVE_ELEVA);
        assertThat(r.estado()).isEqualTo(415);
        assertThat(r.codigo()).isEqualTo("TIPO_NO_ADMITIDO");
        assertThat(r.mensaje()).isEqualTo("No se ha podido enviar la petición. Recarga la página e inténtalo de nuevo.");
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(401);
    }
}
