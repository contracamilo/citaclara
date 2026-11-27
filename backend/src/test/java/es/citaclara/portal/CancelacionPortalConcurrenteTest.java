package es.citaclara.portal;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class CancelacionPortalConcurrenteTest extends PruebaIntegracionBase {

    @Test
    @DisplayName("[002][FR-016][SC-005] 10 cancelaciones simultáneas (5 del portal y 5 de recepción) cancelan una vez y dejan como mucho un aviso")
    void cancelacionesSimultaneas() throws Exception {
        DatosPrueba.EscenarioPortal p = datos.escenarioPortal();
        DatosPrueba.Escenario e = p.e();
        LocalDate dia = DatosPrueba.HOY.plusDays(6);
        try (ExecutorService hilos = Executors.newFixedThreadPool(10)) {
            for (int repeticion = 0; repeticion < 20; repeticion++) {
                long cita = datos.cita(e, e.jorge(), e.sesionFisio(), p.laura(), dia,
                        String.format("%02d:00", 9 + (repeticion % 10)), 45, 4000, "RESERVADA");
                if (repeticion % 10 == 9) {
                    dia = dia.plusDays(1);
                }
                List<Callable<Respuesta>> tareas = new ArrayList<>();
                CountDownLatch salida = new CountDownLatch(1);
                for (int i = 0; i < 5; i++) {
                    ClienteApi laura = clienteEnPortal("eleva", "laura.romero@ejemplo.es");
                    jdbc.update("DELETE FROM solicitud_enlace_portal"); // el límite de enlaces no es lo que se prueba
                    tareas.add(() -> {
                        salida.await();
                        return laura.post("/api/portal/citas/" + cita + "/cancelacion", Map.of("version", 0));
                    });
                    ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
                    tareas.add(() -> {
                        salida.await();
                        return recepcion.post("/api/citas/" + cita + "/estado", Map.of("accion", "CANCELAR", "version", 0));
                    });
                }
                List<Future<Respuesta>> resultados = new ArrayList<>();
                for (Callable<Respuesta> tarea : tareas) {
                    resultados.add(hilos.submit(tarea));
                }
                salida.countDown();
                int exitos = 0;
                for (Future<Respuesta> r : resultados) {
                    Respuesta respuesta = r.get();
                    assertThat(respuesta.estado()).as(respuesta.toString()).isIn(200, 409);
                    if (respuesta.estado() == 200) {
                        exitos++;
                    }
                }
                assertThat(exitos).isGreaterThanOrEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, cita))
                        .isEqualTo("CANCELADA");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM cancelacion_portal WHERE cita_id = ?",
                        Integer.class, cita)).isLessThanOrEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT version FROM cita WHERE id = ?", Integer.class, cita))
                        .as("la cita cambia de estado exactamente una vez").isEqualTo(1);
            }
        }
    }
}
