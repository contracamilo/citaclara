package es.citaclara.cita;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;

/** Principio III y puerta de merge 3: altas simultáneas contra el API real. */
class AltaConcurrenteTest extends PruebaIntegracionBase {

    private static final int HILOS = 10;
    private static final int REPETICIONES = 20;
    /** Inicios dentro de una ventana de 40 min con citas de 45: todas se solapan dos a dos. */
    private static final String[] HORAS = {"10:00", "10:00", "10:15", "10:30", "09:50"};

    @Test
    @DisplayName("[001][FR-013][SC-002][US2-AC5] de 10 altas simultáneas sobre el mismo hueco, exactamente 1 queda registrada (20 repeticiones)")
    void diezAltasSimultaneas() throws Exception {
        Escenario e = datos.escenario();
        List<ClienteApi> recepciones = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            recepciones.add(clienteConSesion("eleva", CLAVE_ELEVA));
        }

        try (ExecutorService hilos = Executors.newFixedThreadPool(HILOS)) {
            for (int repeticion = 0; repeticion < REPETICIONES; repeticion++) {
                LocalDate fecha = MANANA.plusWeeks(repeticion); // cada repetición, un jueves distinto y libre
                CountDownLatch salida = new CountDownLatch(1);
                List<Future<Respuesta>> envios = new ArrayList<>();
                for (int i = 0; i < HILOS; i++) {
                    ClienteApi recepcion = recepciones.get(i);
                    // Mismo inicio y variantes de solape parcial, todas incompatibles entre sí.
                    String hora = HORAS[i % HORAS.length];
                    envios.add(hilos.submit(() -> {
                        salida.await();
                        return recepcion.post("/api/citas", Map.of(
                                "profesionalId", e.jorge(), "servicioId", e.sesionFisio(), "pacienteId", e.ana(),
                                "fecha", fecha.toString(), "horaInicio", hora));
                    }));
                }
                salida.countDown();

                int registradas = 0;
                List<String> rechazos = new ArrayList<>();
                for (Future<Respuesta> envio : envios) {
                    Respuesta r = envio.get();
                    if (r.estado() == 201) {
                        registradas++;
                    } else {
                        assertThat(r.estado()).as(r.texto()).isEqualTo(409);
                        assertThat(r.mensaje()).isNotBlank();
                        rechazos.add(r.codigo());
                    }
                }
                assertThat(registradas).as("repetición %d", repeticion).isEqualTo(1);
                assertThat(rechazos).hasSize(HILOS - 1).allMatch(c -> c.equals("HUECO_RECIEN_OCUPADO") || c.equals("SOLAPE"));
            }
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cita", Integer.class)).isEqualTo(REPETICIONES);
        assertThat(solapes()).as("[001][SC-001] solapes registrados").isZero();
    }

    private int solapes() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM cita a JOIN cita b
                  ON a.profesional_id = b.profesional_id AND a.id < b.id
                 AND a.estado IN ('RESERVADA', 'COMPLETADA') AND b.estado IN ('RESERVADA', 'COMPLETADA')
                 AND a.inicio < b.fin AND b.inicio < a.fin
                """, Integer.class);
    }
}
