package es.citaclara.recordatorio;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaRecordatoriosBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static org.assertj.core.api.Assertions.assertThat;

class CancelacionConcurrenteTest extends PruebaRecordatoriosBase {

    private static final int REPETICIONES = 10;

    @Autowired
    ServicioRecordatorios servicio;

    private Escenario e;
    private final List<Long> citas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        e = datos.escenario();
        for (int i = 0; i < REPETICIONES; i++) {
            citas.add(reservadaEn(e, e.maria(), e.ana(), Duration.ofHours(25 + i)));
        }
        assertThat(servicio.generar()).isEqualTo(REPETICIONES);
    }

    @Test
    @DisplayName("[003][FR-019][US2-AC7] la doble confirmación simultánea del paciente cancela una sola vez y ambas respuestas son coherentes (10 repeticiones)")
    void dobleConfirmacion() throws Exception {
        ClienteApi pestanaA = cliente();
        ClienteApi pestanaB = cliente();
        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            for (long cita : citas) {
                String enlace = enlaceDe(cita);
                CountDownLatch salida = new CountDownLatch(1);
                Future<Respuesta> a = hilos.submit(() -> {
                    salida.await();
                    return pestanaA.post("/api/cancelacion/" + enlace, Map.of());
                });
                Future<Respuesta> b = hilos.submit(() -> {
                    salida.await();
                    return pestanaB.post("/api/cancelacion/" + enlace, Map.of());
                });
                salida.countDown();

                for (Respuesta r : List.of(a.get(), b.get())) {
                    assertThat(r.estado()).as("cita %d", cita).isEqualTo(200);
                    assertThat(r.cuerpo().path("situacion").asString()).isEqualTo("YA_CERRADA");
                    assertThat(r.cuerpo().path("canceladaPorPaciente").asBoolean()).isTrue();
                }
                assertThat(jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, cita)).isEqualTo("CANCELADA");
                assertThat(jdbc.queryForObject("SELECT version FROM cita WHERE id = ?", Integer.class, cita))
                        .as("se cancela una sola vez").isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("[003][FR-019][US2-AC4] paciente y recepción cancelando a la vez: solo se aplica el primero y el otro ve el estado real (10 repeticiones)")
    void pacienteYRecepcionALaVez() throws Exception {
        ClienteApi pestanaPaciente = cliente();
        ClienteApi recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            for (long cita : citas) {
                String enlace = enlaceDe(cita);
                CountDownLatch salida = new CountDownLatch(1);
                Future<Respuesta> delPaciente = hilos.submit(() -> {
                    salida.await();
                    return pestanaPaciente.post("/api/cancelacion/" + enlace, Map.of());
                });
                Future<Respuesta> deRecepcion = hilos.submit(() -> {
                    salida.await();
                    return recepcion.post("/api/citas/" + cita + "/estado", Map.of("accion", "CANCELAR", "version", 0));
                });
                salida.countDown();
                Respuesta p = delPaciente.get();
                Respuesta r = deRecepcion.get();

                assertThat(List.of(p.estado(), r.estado())).as("cita %d", cita).containsExactlyInAnyOrder(200, 409);
                String quien = jdbc.queryForObject("SELECT cancelada_por FROM cita WHERE id = ?", String.class, cita);
                if (p.estado() == 200) {
                    assertThat(quien).isEqualTo("PACIENTE");
                    assertThat(r.codigo()).isEqualTo("ESTADO_YA_CAMBIADO");
                } else {
                    assertThat(quien).isNull();
                    assertThat(p.codigo()).isEqualTo("ESTADO_YA_CAMBIADO");
                    assertThat(p.mensaje()).isEqualTo("Esta cita ya no está activa. Si tienes dudas, llama a la clínica.");
                }
                assertThat(jdbc.queryForObject("SELECT estado FROM cita WHERE id = ?", String.class, cita)).isEqualTo("CANCELADA");
            }
        }
    }
}
