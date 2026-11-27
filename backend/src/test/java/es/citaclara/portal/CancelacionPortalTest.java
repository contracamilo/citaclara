package es.citaclara.portal;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.MADRID;
import static org.assertj.core.api.Assertions.assertThat;

class CancelacionPortalTest extends PruebaIntegracionBase {

    private static final String LAURA = "laura.romero@ejemplo.es";
    /** Viernes 27/11/2026: la cita de Laura con Jorge es de 09:45 a 10:30. */
    private static final LocalDate VIERNES = LocalDate.of(2026, 11, 27);

    private DatosPrueba.EscenarioPortal p;
    private DatosPrueba.Escenario e;
    private long cita;

    @BeforeEach
    void preparar() {
        p = datos.escenarioPortal();
        e = p.e();
        cita = datos.cita(e, e.jorge(), e.sesionFisio(), p.laura(), VIERNES, "09:45", 45, 4000, "RESERVADA");
        reloj.fijar(ZonedDateTime.of(2026, 11, 26, 9, 0, 0, 0, MADRID)); // jueves, 24 h 45 min antes
    }

    private Respuesta cancelar(ClienteApi cliente, long citaId, int version) {
        return cliente.post("/api/portal/citas/" + citaId + "/cancelacion", Map.of("version", version));
    }

    private String estadoEnBd(long citaId) {
        return jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, citaId);
    }

    private int avisos(long citaId) {
        return jdbc.queryForObject("SELECT count(*) FROM cancelacion_portal WHERE cita_id = ?", Integer.class, citaId);
    }

    @Test
    @DisplayName("[002][FR-012][FR-015][FR-017][US3-AC1][US3-AC2] Laura cancela con más de 24 h, la cita queda cancelada, se registra el origen y el hueco queda libre")
    void cancelaEnPlazo() {
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        JsonNode antes = laura.get("/api/portal/citas").cuerpo().path("proximas").get(0);
        assertThat(antes.path("cancelable").asBoolean()).isTrue();

        Respuesta r = cancelar(laura, cita, antes.path("version").asInt());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().path("estadoPortal").asString()).isEqualTo("CANCELADA");
        assertThat(estadoEnBd(cita)).isEqualTo("CANCELADA");
        Timestamp cancelada = jdbc.queryForObject(
                "SELECT cancelada_en FROM cancelacion_portal WHERE cita_id = ?", Timestamp.class, cita);
        assertThat(cancelada.toInstant()).isEqualTo(reloj.instant());
        assertThat(jdbc.queryForObject("SELECT revisada_en FROM cancelacion_portal WHERE cita_id = ?", Timestamp.class, cita))
                .isNull();

        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        JsonNode agenda = recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + VIERNES).cuerpo();
        assertThat(agenda.path("huecos").size()).isEqualTo(1);
        assertThat(agenda.path("huecos").get(0).path("horaInicio").asString()).isEqualTo("09:00");
        assertThat(agenda.path("huecos").get(0).path("horaFin").asString()).isEqualTo("20:00");
    }

    @Test
    @DisplayName("[002][FR-013][FR-014][SC-003][US3-AC4][US3-AC5] a las 24 horas exactas se rechaza FUERA_DE_PLAZO; recepción sí puede cancelarla")
    void fueraDePlazo() {
        reloj.fijar(ZonedDateTime.of(2026, 11, 26, 9, 44, 0, 0, MADRID));
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        assertThat(laura.get("/api/portal/citas").cuerpo().path("proximas").get(0).path("cancelable").asBoolean()).isTrue();

        reloj.fijar(ZonedDateTime.of(2026, 11, 26, 9, 45, 0, 0, MADRID)); // confirma justo al vencer el plazo
        Respuesta r = cancelar(laura, cita, 0);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.codigo()).isEqualTo("FUERA_DE_PLAZO");
        assertThat(r.mensaje()).isEqualTo("Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.");
        assertThat(estadoEnBd(cita)).isEqualTo("RESERVADA");
        JsonNode vista = laura.get("/api/portal/citas").cuerpo().path("proximas").get(0);
        assertThat(vista.path("cancelable").asBoolean()).isFalse();
        assertThat(vista.path("motivoNoCancelable").asString()).isEqualTo(r.mensaje());

        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        Respuesta desdeRecepcion = recepcion.post("/api/citas/" + cita + "/estado", Map.of("accion", "CANCELAR", "version", 0));
        assertThat(desdeRecepcion.estado()).isEqualTo(200);
        assertThat(avisos(cita)).isZero();
    }

    @Test
    @DisplayName("[002][SC-003] una cita ya iniciada nunca se cancela desde el portal")
    void yaIniciada() {
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        reloj.fijar(ZonedDateTime.of(2026, 11, 27, 9, 50, 0, 0, MADRID));
        laura = clienteEnPortal("eleva", LAURA);

        Respuesta r = cancelar(laura, cita, 0);

        assertThat(r.estado()).isIn(409, 422);
        assertThat(estadoEnBd(cita)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[002][FR-016][US3-AC6] si recepción cambia la cita antes, el portal recibe ESTADO_YA_CAMBIADO con su mensaje y su formato")
    void conflictoConRecepcion() {
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        assertThat(recepcion.post("/api/citas/" + cita + "/estado", Map.of("accion", "CANCELAR", "version", 0)).estado())
                .isEqualTo(200);

        Respuesta r = cancelar(laura, cita, 0);

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.codigo()).isEqualTo("ESTADO_YA_CAMBIADO");
        assertThat(r.mensaje()).isEqualTo("Esta cita acaba de cambiar. Revisa su estado.");
        JsonNode actual = r.cuerpo().path("citaActual");
        assertThat(actual.path("estadoPortal").asString()).isEqualTo("CANCELADA");
        assertThat(actual.path("profesional").asString()).isEqualTo("Jorge");
        assertThat(actual.has("acciones")).isFalse(); // no es el formato del panel
        assertThat(avisos(cita)).isZero();
    }

    @Test
    @DisplayName("[002][US3-AC7] dos confirmaciones seguidas del mismo paciente cancelan una sola vez y no dan error")
    void dobleConfirmacion() {
        ClienteApi laura = clienteEnPortal("eleva", LAURA);

        Respuesta primera = cancelar(laura, cita, 0);
        Respuesta segunda = cancelar(laura, cita, 0);

        assertThat(primera.estado()).isEqualTo(200);
        assertThat(segunda.estado()).isEqualTo(200);
        assertThat(segunda.cuerpo().path("estadoPortal").asString()).isEqualTo("CANCELADA");
        assertThat(avisos(cita)).isEqualTo(1);
    }

    @Test
    @DisplayName("[002][FR-028] la cita de otro paciente o de otra clínica responde 404 y no cambia nada")
    void citaAjena() {
        long deAna = datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), VIERNES, "12:00", 45, 4000, "RESERVADA");
        DatosPrueba.OtraClinica o = p.otra();
        long deOtraClinica = datos.cita(o.clinica(), o.profesional(), o.servicio(), p.teresaOtraClinica(),
                DatosPrueba.instante(VIERNES, "12:00"), DatosPrueba.instante(VIERNES, "12:45"), 4000, "RESERVADA");
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        ClienteApi teresa = clienteEnPortal("eleva", "teresa.mendez@ejemplo.es");

        assertThat(cancelar(laura, deAna, 0).estado()).isEqualTo(404);
        assertThat(cancelar(teresa, cita, 0).estado()).isEqualTo(404);
        assertThat(cancelar(teresa, deOtraClinica, 0).estado()).isEqualTo(404);
        assertThat(cancelar(laura, 999_999, 0).estado()).isEqualTo(404);
        assertThat(estadoEnBd(deAna)).isEqualTo("RESERVADA");
        assertThat(estadoEnBd(cita)).isEqualTo("RESERVADA");
        assertThat(estadoEnBd(deOtraClinica)).isEqualTo("RESERVADA");
    }

    @Test
    @DisplayName("[002][FR-018] el portal no crea, mueve ni reactiva citas")
    void noCreaNiReactiva() {
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        cancelar(laura, cita, 0);

        assertThat(laura.post("/api/portal/citas", Map.of("profesionalId", e.jorge())).estado()).isIn(404, 405);
        assertThat(cancelar(laura, cita, 1).estado()).isEqualTo(200);
        assertThat(estadoEnBd(cita)).isEqualTo("CANCELADA");
        assertThat(laura.post("/api/citas", Map.of("profesionalId", e.jorge())).estado()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cita", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("[002][FR-005][US1-AC5] con la sesión caducada, cancelar responde 401 y la cita sigue reservada")
    void sesionCaducadaNoCancela() {
        ClienteApi laura = clienteEnPortal("eleva", LAURA);
        reloj.avanzar(Duration.ofMinutes(30));

        Respuesta r = cancelar(laura, cita, 0);

        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SESION_PORTAL_CADUCADA");
        assertThat(estadoEnBd(cita)).isEqualTo("RESERVADA");
    }
}
