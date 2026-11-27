package es.citaclara.portal;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static es.citaclara.soporte.DatosPrueba.MADRID;
import static org.assertj.core.api.Assertions.assertThat;

class AvisoCancelacionesTest extends PruebaIntegracionBase {

    private static final LocalDate VIERNES = LocalDate.of(2026, 11, 27);
    private static final String LAURA = "laura.romero@ejemplo.es";

    private DatosPrueba.EscenarioPortal p;
    private DatosPrueba.Escenario e;
    private long citaLaura;

    @BeforeEach
    void preparar() {
        p = datos.escenarioPortal();
        e = p.e();
        citaLaura = datos.cita(e, e.jorge(), e.sesionFisio(), p.laura(), VIERNES, "09:45", 45, 4000, "RESERVADA");
        reloj.fijar(ZonedDateTime.of(2026, 11, 26, 9, 0, 0, 0, MADRID));
    }

    private void cancelarDesdePortal(String email, long cita) {
        Respuesta r = clienteEnPortal("eleva", email).post("/api/portal/citas/" + cita + "/cancelacion", Map.of("version", 0));
        assertThat(r.estado()).as(r.toString()).isEqualTo(200);
    }

    private JsonNode aviso(ClienteApi recepcion) {
        Respuesta r = recepcion.get("/api/cancelaciones-portal");
        assertThat(r.estado()).isEqualTo(200);
        return r.cuerpo();
    }

    @Test
    @DisplayName("[002][FR-019][SC-008][US4-AC1] tras la cancelación de Laura desde el portal, recepción ve 1 pendiente")
    void contador() {
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        assertThat(aviso(recepcion).path("pendientes").asInt()).isZero();

        cancelarDesdePortal(LAURA, citaLaura);

        assertThat(aviso(recepcion).path("pendientes").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("[002][FR-020][US4-AC2] cada pendiente trae paciente, profesional, hueco, servicio y momento de la cancelación, de la más reciente a la más antigua")
    void lista() {
        cancelarDesdePortal(LAURA, citaLaura);
        long deTeresa = datos.cita(e, e.maria(), e.primeraFisio(), p.teresa(), VIERNES, "12:00", 60, 5000, "RESERVADA");
        reloj.avanzar(Duration.ofMinutes(5));
        cancelarDesdePortal("teresa.mendez@ejemplo.es", deTeresa);

        JsonNode cuerpo = aviso(clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA));

        assertThat(cuerpo.path("pendientes").asInt()).isEqualTo(2);
        JsonNode primera = cuerpo.path("cancelaciones").get(0);
        assertThat(primera.path("paciente").asString()).isEqualTo("Teresa Méndez");
        JsonNode laura = cuerpo.path("cancelaciones").get(1);
        assertThat(laura.path("citaId").asLong()).isEqualTo(citaLaura);
        assertThat(laura.path("paciente").asString()).isEqualTo("Laura Romero");
        assertThat(laura.path("profesional").asString()).isEqualTo("Jorge");
        assertThat(laura.path("fecha").asString()).isEqualTo("2026-11-27");
        assertThat(laura.path("horaInicio").asString()).isEqualTo("09:45");
        assertThat(laura.path("horaFin").asString()).isEqualTo("10:30");
        assertThat(laura.path("servicio").asString()).isEqualTo("Sesión fisioterapia");
        assertThat(laura.path("canceladaFecha").asString()).isEqualTo("2026-11-26");
        assertThat(laura.path("canceladaHora").asString()).isEqualTo("09:00");
        assertThat(laura.path("huecoPasado").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("[002][FR-021][US4-AC3] marcar como revisada la saca de pendientes")
    void revisar() {
        cancelarDesdePortal(LAURA, citaLaura);
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);

        assertThat(recepcion.post("/api/cancelaciones-portal/" + citaLaura + "/revision", Map.of()).estado()).isEqualTo(204);

        JsonNode cuerpo = aviso(recepcion);
        assertThat(cuerpo.path("pendientes").asInt()).isZero();
        assertThat(cuerpo.path("cancelaciones").size()).isZero();
    }

    @Test
    @DisplayName("[002][FR-022][US4-AC4] una cancelación hecha por recepción no genera aviso")
    void cancelacionDeRecepcion() {
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        assertThat(recepcion.post("/api/citas/" + citaLaura + "/estado", Map.of("accion", "CANCELAR", "version", 0)).estado())
                .isEqualTo(200);

        assertThat(aviso(recepcion).path("pendientes").asInt()).isZero();
    }

    @Test
    @DisplayName("[002][FR-021][US4-AC5] marcar dos veces o a la vez desde dos puestos no da error y revisa una sola vez")
    void revisionIdempotente() throws Exception {
        cancelarDesdePortal(LAURA, citaLaura);
        ClienteApi puesto1 = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        ClienteApi puesto2 = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        String ruta = "/api/cancelaciones-portal/" + citaLaura + "/revision";

        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Integer>> resultados = new ArrayList<>();
            for (ClienteApi puesto : List.of(puesto1, puesto2)) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return puesto.post(ruta, Map.of()).estado();
                }));
            }
            salida.countDown();
            for (Future<Integer> r : resultados) {
                assertThat(r.get()).isEqualTo(204);
            }
        }
        java.sql.Timestamp revisada = jdbc.queryForObject(
                "SELECT revisada_en FROM cancelacion_portal WHERE cita_id = ?", java.sql.Timestamp.class, citaLaura);
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(puesto1.post(ruta, Map.of()).estado()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT revisada_en FROM cancelacion_portal WHERE cita_id = ?",
                java.sql.Timestamp.class, citaLaura)).isEqualTo(revisada);
    }

    @Test
    @DisplayName("[002][FR-020][US4-AC6] una pendiente cuyo hueco ya ha pasado sigue en la lista marcada como hueco pasado")
    void huecoPasado() {
        cancelarDesdePortal(LAURA, citaLaura);
        reloj.fijar(ZonedDateTime.of(2026, 11, 27, 9, 45, 0, 0, MADRID));

        JsonNode cuerpo = aviso(clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA));

        assertThat(cuerpo.path("pendientes").asInt()).isEqualTo(1);
        assertThat(cuerpo.path("cancelaciones").get(0).path("huecoPasado").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("[002][US4-AC7] otra clínica nunca ve ni revisa las cancelaciones de Clínica Eleva")
    void aislamiento() {
        cancelarDesdePortal(LAURA, citaLaura);
        ClienteApi otra = clienteConSesion("otra", DatosPrueba.CLAVE_OTRA);

        assertThat(aviso(otra).path("pendientes").asInt()).isZero();
        assertThat(otra.post("/api/cancelaciones-portal/" + citaLaura + "/revision", Map.of()).estado()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT revisada_en IS NULL FROM cancelacion_portal WHERE cita_id = ?",
                Boolean.class, citaLaura)).isTrue();
    }

    @Test
    @DisplayName("[002][FR-029] la sesión del portal no da acceso al aviso de recepción")
    void portalNoVeElAviso() {
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        assertThat(laura.get("/api/cancelaciones-portal").estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("[002][FR-023] revisar una cancelación no cambia la agenda del día")
    void agendaSinCambios() {
        cancelarDesdePortal(LAURA, citaLaura);
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        String ruta = "/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + VIERNES;
        String antes = recepcion.get(ruta).texto();

        recepcion.post("/api/cancelaciones-portal/" + citaLaura + "/revision", Map.of());

        assertThat(recepcion.get(ruta).texto()).isEqualTo(antes);
    }
}
