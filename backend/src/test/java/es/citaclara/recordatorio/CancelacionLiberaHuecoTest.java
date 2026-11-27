package es.citaclara.recordatorio;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;

/** Principio III: cancelar por el enlace libera el hueco y no abre ninguna vía a un solape. */
class CancelacionLiberaHuecoTest extends PruebaRecordatoriosBase {

    // El reloj de pruebas es el miércoles 25/11/2026 12:00: +30 h = jueves 26/11/2026 18:00.
    private static final String FECHA = "2026-11-26";
    private static final String HORA = "18:00";

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;
    private long cita;
    private String enlace;
    private ClienteApi recepcion;

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        cita = reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(30));
        servicio.generar();
        enlace = enlaceDe(cita);
        recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
    }

    private Respuesta reservar(ClienteApi cliente, long paciente) {
        return cliente.post("/api/citas", Map.of("profesionalId", e.maria(), "servicioId", e.sesionFisio(),
                "pacienteId", paciente, "fecha", FECHA, "horaInicio", HORA));
    }

    private JsonNode agendaDeMaria() {
        return recepcion.get("/api/agenda?profesionalId=" + e.maria() + "&fecha=" + FECHA).cuerpo();
    }

    @Test
    @DisplayName("[003][FR-015][SC-003][US2-AC2] tras cancelar por el enlace el hueco figura libre de inmediato y puede reservarse de nuevo sin solape")
    void huecoLibreYReservable() {
        assertThat(reservar(recepcion, e.pedro()).estado()).as("ocupado antes de cancelar").isEqualTo(409);

        assertThat(cliente().post("/api/cancelacion/" + enlace, Map.of()).estado()).isEqualTo(200);

        JsonNode huecos = agendaDeMaria().path("huecos");
        boolean libre = false;
        for (JsonNode hueco : huecos) { // los huecos contiguos se unen: basta con que uno contenga las 18:00
            libre |= hueco.path("horaInicio").asString().compareTo(HORA) <= 0 && hueco.path("horaFin").asString().compareTo(HORA) > 0;
        }
        assertThat(libre).as("la franja de las 18:00 queda libre: %s", huecos).isTrue();
        assertThat(reservar(recepcion, e.pedro()).estado()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cita WHERE profesional_id = ? AND estado = 'RESERVADA'",
                Integer.class, e.maria())).isEqualTo(1);
    }

    @Test
    @DisplayName("[003][FR-015][US2-AC2] reservas simultáneas del hueco liberado: exactamente una prospera y nunca hay solape")
    void reservasSimultaneasDelHuecoLiberado() throws Exception {
        cliente().post("/api/cancelacion/" + enlace, Map.of());
        ClienteApi otraRecepcion = clienteConSesion("eleva", CLAVE_ELEVA);
        CountDownLatch salida = new CountDownLatch(1);
        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            Future<Respuesta> a = hilos.submit(() -> {
                salida.await();
                return reservar(recepcion, e.pedro());
            });
            Future<Respuesta> b = hilos.submit(() -> {
                salida.await();
                return reservar(otraRecepcion, e.ana());
            });
            salida.countDown();
            assertThat(List.of(a.get().estado(), b.get().estado())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cita WHERE profesional_id = ? AND estado = 'RESERVADA'",
                Integer.class, e.maria())).isEqualTo(1);
    }

    @Test
    @DisplayName("[003][FR-016][US2-AC8] la agenda distingue la cita cancelada por el paciente de la cancelada por la clínica")
    void agendaDistingueQuienCancelo() {
        long otraCita = reservadaEn(e, e.jorge(), e.pedro(), Duration.ofHours(30));
        cliente().post("/api/cancelacion/" + enlace, Map.of());
        assertThat(recepcion.post("/api/citas/" + otraCita + "/estado", Map.of("accion", "CANCELAR", "version", 0)).estado()).isEqualTo(200);

        JsonNode deMaria = agendaDeMaria().path("citas").get(0);
        assertThat(deMaria.path("estado").asString()).isEqualTo("CANCELADA");
        assertThat(deMaria.path("canceladaPor").asString()).isEqualTo("PACIENTE");
        JsonNode deJorge = recepcion.get("/api/agenda?profesionalId=" + e.jorge() + "&fecha=" + FECHA).cuerpo().path("citas").get(0);
        assertThat(deJorge.path("estado").asString()).isEqualTo("CANCELADA");
        assertThat(deJorge.path("canceladaPor").asString(null)).isNull();
    }
}
