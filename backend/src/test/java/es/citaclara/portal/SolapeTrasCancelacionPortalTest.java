package es.citaclara.portal;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/** Principio III (puerta de merge 3, research R9): cancelar desde el portal nunca abre la puerta a un solape. */
class SolapeTrasCancelacionPortalTest extends PruebaIntegracionBase {

    private static final String LAURA = "laura.romero@ejemplo.es";

    private DatosPrueba.EscenarioPortal p;
    private DatosPrueba.Escenario e;
    private final LocalDate dia = DatosPrueba.HOY.plusDays(6); // martes, más de 24 h

    @BeforeEach
    void preparar() {
        p = datos.escenarioPortal();
        e = p.e();
    }

    private Map<String, Object> alta(String hora) {
        return Map.of("profesionalId", e.jorge(), "servicioId", e.sesionFisio(), "pacienteId", e.ana(),
                "fecha", dia.toString(), "horaInicio", hora);
    }

    private int solapesDeJorge() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM cita a JOIN cita b ON a.id < b.id AND a.profesional_id = b.profesional_id
                WHERE a.profesional_id = ? AND a.estado IN ('RESERVADA','COMPLETADA') AND b.estado IN ('RESERVADA','COMPLETADA')
                  AND tstzrange(a.inicio, a.fin, '[)') && tstzrange(b.inicio, b.fin, '[)')
                """, Integer.class, e.jorge());
    }

    @Test
    @DisplayName("[002][FR-015] tras cancelar desde el portal, una alta de recepción en el mismo hueco se registra")
    void huecoRealmenteLibre() {
        long cita = datos.cita(e, e.jorge(), e.sesionFisio(), p.laura(), dia, "10:00", 45, 4000, "RESERVADA");
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        assertThat(recepcion.post("/api/citas", alta("10:00")).estado()).isEqualTo(409);

        assertThat(clienteEnPortal("eleva", LAURA).post("/api/portal/citas/" + cita + "/cancelacion", Map.of("version", 0))
                .estado()).isEqualTo(200);

        assertThat(recepcion.post("/api/citas", alta("10:00")).estado()).isEqualTo(201);
        assertThat(solapesDeJorge()).isZero();
    }

    @Test
    @DisplayName("[002][FR-015] tras cancelar desde el portal, 10 altas simultáneas en el hueco liberado dejan exactamente una")
    void altasSimultaneasEnHuecoLiberado() throws Exception {
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        try (ExecutorService hilos = Executors.newFixedThreadPool(10)) {
            for (int repeticion = 0; repeticion < 20; repeticion++) {
                jdbc.update("DELETE FROM cita");
                jdbc.update("DELETE FROM solicitud_enlace_portal");
                long cita = datos.cita(e, e.jorge(), e.sesionFisio(), p.laura(), dia, "10:00", 45, 4000, "RESERVADA");
                assertThat(clienteEnPortal("eleva", LAURA)
                        .post("/api/portal/citas/" + cita + "/cancelacion", Map.of("version", 0)).estado()).isEqualTo(200);

                CountDownLatch salida = new CountDownLatch(1);
                List<Future<Integer>> resultados = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    String hora = i % 2 == 0 ? "10:00" : "10:15"; // coincidencia exacta y solape parcial
                    resultados.add(hilos.submit(() -> {
                        salida.await();
                        return recepcion.post("/api/citas", alta(hora)).estado();
                    }));
                }
                salida.countDown();
                int registradas = 0;
                for (Future<Integer> r : resultados) {
                    int estado = r.get();
                    assertThat(estado).isIn(201, 409);
                    if (estado == 201) {
                        registradas++;
                    }
                }
                assertThat(registradas).isEqualTo(1);
                assertThat(solapesDeJorge()).isZero();
            }
        }
    }

    @Test
    @DisplayName("[002][FR-015] cancelación del portal y alta de recepción en paralelo nunca dejan dos citas solapadas")
    void cancelacionYAltaEnParalelo() throws Exception {
        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            for (int repeticion = 0; repeticion < 20; repeticion++) {
                jdbc.update("DELETE FROM cita");
                jdbc.update("DELETE FROM solicitud_enlace_portal");
                long cita = datos.cita(e, e.jorge(), e.sesionFisio(), p.laura(), dia, "10:00", 45, 4000, "RESERVADA");
                ClienteApi laura = clienteEnPortal("eleva", LAURA);
                CountDownLatch salida = new CountDownLatch(1);
                Future<Respuesta> cancelacion = hilos.submit(() -> {
                    salida.await();
                    return laura.post("/api/portal/citas/" + cita + "/cancelacion", Map.of("version", 0));
                });
                Future<Respuesta> nueva = hilos.submit(() -> {
                    salida.await();
                    return recepcion.post("/api/citas", alta("10:00"));
                });
                salida.countDown();
                assertThat(cancelacion.get().estado()).isEqualTo(200);
                assertThat(nueva.get().estado()).isIn(201, 409);
                assertThat(solapesDeJorge()).isZero();
            }
        }
    }
}
