package es.citaclara.cita;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.HOY;
import static org.assertj.core.api.Assertions.assertThat;

class CambioEstadoConcurrenteTest extends PruebaIntegracionBase {

    private static final int REPETICIONES = 20;

    @Test
    @DisplayName("[001][FR-025][US3-AC7] de dos cambios simultáneos solo se aplica uno y el otro ve el estado real (20 repeticiones)")
    void cambiosSimultaneos() throws Exception {
        Escenario e = datos.escenario();
        ClienteApi pestanaA = clienteConSesion("eleva", CLAVE_ELEVA);
        ClienteApi pestanaB = clienteConSesion("eleva", CLAVE_ELEVA);

        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < REPETICIONES; i++) {
                // Citas ya empezadas (hoy a las 10:00 o días anteriores): admiten completar y no asistida.
                long cita = datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), HOY.minusWeeks(i), "10:00", 45, 4000, "RESERVADA");
                CountDownLatch salida = new CountDownLatch(1);
                Future<Respuesta> a = hilos.submit(() -> {
                    salida.await();
                    return pestanaA.post("/api/citas/" + cita + "/estado", Map.of("accion", "COMPLETAR", "version", 0));
                });
                Future<Respuesta> b = hilos.submit(() -> {
                    salida.await();
                    return pestanaB.post("/api/citas/" + cita + "/estado", Map.of("accion", "MARCAR_NO_ASISTIDA", "version", 0));
                });
                salida.countDown();

                List<Respuesta> respuestas = List.of(a.get(), b.get());
                assertThat(respuestas).extracting(Respuesta::estado).as("repetición %d", i).containsExactlyInAnyOrder(200, 409);
                Respuesta ganadora = respuestas.stream().filter(r -> r.estado() == 200).findFirst().orElseThrow();
                Respuesta perdedora = respuestas.stream().filter(r -> r.estado() == 409).findFirst().orElseThrow();
                String estadoReal = ganadora.cuerpo().path("estado").asString();
                String nombre = estadoReal.equals("COMPLETADA") ? "Completada" : "No asistida";

                assertThat(perdedora.codigo()).isEqualTo("ESTADO_YA_CAMBIADO");
                assertThat(perdedora.mensaje()).isEqualTo("Otra persona acaba de cambiar esta cita. Ahora está: " + nombre + ".");
                assertThat(perdedora.cuerpo().path("citaActual").path("estado").asString()).isEqualTo(estadoReal);
                assertThat(jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, cita)).isEqualTo(estadoReal);
            }
        }
    }

    @Test
    @DisplayName("[001][FR-025][US3-AC7] una petición tardía con una versión antigua ve el estado real")
    void versionAntigua() {
        Escenario e = datos.escenario();
        ClienteApi recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
        long cita = datos.cita(e, e.jorge(), e.sesionFisio(), e.ana(), HOY, "10:00", 45, 4000, "RESERVADA");
        assertThat(recepcion.post("/api/citas/" + cita + "/estado", Map.of("accion", "COMPLETAR", "version", 0)).estado())
                .isEqualTo(200);

        Respuesta tardia = recepcion.post("/api/citas/" + cita + "/estado", Map.of("accion", "MARCAR_NO_ASISTIDA", "version", 0));
        assertThat(tardia.estado()).isEqualTo(409);
        assertThat(tardia.mensaje()).isEqualTo("Otra persona acaba de cambiar esta cita. Ahora está: Completada.");
        assertThat(tardia.cuerpo().path("citaActual").path("version").asInt()).isEqualTo(1);
    }
}
