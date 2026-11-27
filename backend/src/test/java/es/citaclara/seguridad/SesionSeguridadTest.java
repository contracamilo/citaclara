package es.citaclara.seguridad;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-004: sesión, cookie y comportamiento ante claves erróneas y cierre de sesión. */
class SesionSeguridadTest extends PruebaIntegracionBase {

    @BeforeEach
    void preparar() {
        datos.escenario();
    }

    @Test
    @DisplayName("[005][FR-004] la cookie de sesión es HttpOnly y SameSite=Strict, y no lleva la clave")
    void cabeceraDeLaCookie() {
        ClienteApi cliente = cliente();
        Respuesta r = cliente.post("/api/clinicas/eleva/sesion", Map.of("clave", CLAVE_ELEVA));
        assertThat(r.estado()).isEqualTo(204);
        List<String> cookies = r.cabeceras().allValues("Set-Cookie");
        assertThat(cookies).hasSize(1);
        String cookie = cookies.get(0);
        assertThat(cookie).startsWith("CITACLARA_SESION=");
        assertThat(cookie).containsIgnoringCase("HttpOnly");
        assertThat(cookie).containsIgnoringCase("SameSite=Strict");
        assertThat(cookie).doesNotContain(CLAVE_ELEVA);
    }

    @Test
    @DisplayName("[005][FR-004] al iniciar sesión se genera un identificador nuevo (sin fijación de sesión)")
    void sinFijacionDeSesion() {
        ClienteApi cliente = cliente();
        cliente.post("/api/clinicas/eleva/sesion", Map.of("clave", CLAVE_ELEVA));
        String primera = valorCookie(cliente);
        cliente.post("/api/clinicas/eleva/sesion", Map.of("clave", CLAVE_ELEVA));
        String segunda = valorCookie(cliente);
        assertThat(primera).isNotBlank();
        assertThat(segunda).isNotBlank().isNotEqualTo(primera);
    }

    @Test
    @DisplayName("[005][FR-004] una sesión anterior no vale tras cerrar sesión")
    void cierreDeSesion() {
        ClienteApi cliente = clienteConSesion("eleva", CLAVE_ELEVA);
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(200);
        assertThat(cliente.delete("/api/sesion").estado()).isEqualTo(204);
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("[005][FR-004] la sesión caduca de forma absoluta a las 8 h aunque haya actividad")
    void caducidadAbsoluta() {
        ClienteApi cliente = clienteConSesion("eleva", CLAVE_ELEVA);
        reloj.avanzar(Duration.ofHours(7).plusMinutes(59));
        assertThat(cliente.get("/api/sesion").estado()).isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(2));
        Respuesta caducada = cliente.get("/api/sesion");
        assertThat(caducada.estado()).isEqualTo(401);
        assertThat(caducada.codigo()).isEqualTo("SESION_CADUCADA");
    }

    @Test
    @DisplayName("[005][FR-004] clave incorrecta y clínica inexistente son indistinguibles (mensaje y estado)")
    void sinEnumeracionDeClinicas() {
        Respuesta claveMal = cliente().post("/api/clinicas/eleva/sesion", Map.of("clave", "mala"));
        Respuesta sinClinica = cliente().post("/api/clinicas/no-existe/sesion", Map.of("clave", "mala"));
        assertThat(claveMal.estado()).isEqualTo(sinClinica.estado());
        assertThat(claveMal.codigo()).isEqualTo(sinClinica.codigo());
        assertThat(claveMal.mensaje()).isEqualTo(sinClinica.mensaje());
    }

    @Test
    @DisplayName("[005][FR-004] el POST de acceso solo admite JSON (defensa CSRF)")
    void soloJson() {
        Respuesta r = cliente().postCrudo("/api/clinicas/eleva/sesion", "application/x-www-form-urlencoded",
                "clave=" + CLAVE_ELEVA);
        assertThat(r.estado()).isEqualTo(415);
        assertThat(r.codigo()).isEqualTo("TIPO_NO_ADMITIDO");
    }

    private static String valorCookie(ClienteApi cliente) {
        return cliente.cookies().getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("CITACLARA_SESION"))
                .map(HttpCookie::getValue).findFirst().orElse("");
    }
}
