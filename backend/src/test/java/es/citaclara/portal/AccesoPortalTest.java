package es.citaclara.portal;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class AccesoPortalTest extends PruebaIntegracionBase {

    private static final String TERESA = "teresa.mendez@ejemplo.es";
    private static final String NADIE = "nadie@ejemplo.es";

    private DatosPrueba.EscenarioPortal p;

    @BeforeEach
    void preparar() {
        p = datos.escenarioPortal();
    }

    @Test
    @DisplayName("[002][FR-001][FR-002][US1-AC1][US1-AC2] Teresa pide el enlace, lo recibe en su email y entra sin cuenta ni contraseña")
    void entraConElEnlace() {
        Integer pacientesAntes = jdbc.queryForObject("SELECT count(*) FROM paciente", Integer.class);
        ClienteApi teresa = cliente();

        assertThat(teresa.pedirEnlace("eleva", TERESA).estado()).isEqualTo(202);

        assertThat(buzon.enviadosA(TERESA)).hasSize(1);
        String url = buzon.ultimoEnlace(TERESA);
        assertThat(url).startsWith("http://localhost:8080/eleva/mis-citas#enlace=");
        assertThat(buzon.enviadosA(TERESA).getFirst().clinica()).isEqualTo("Clínica Eleva");
        assertThat(teresa.abrirSesionPortal("eleva", buzon.secreto(url)).estado()).isEqualTo(204);
        assertThat(teresa.get("/api/portal/citas").estado()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente", Integer.class)).isEqualTo(pacientesAntes);
    }

    @Test
    @DisplayName("[002][FR-002] el email se compara sin distinguir mayúsculas ni espacios")
    void emailSinMayusculasNiEspacios() {
        assertThat(cliente().pedirEnlace("eleva", "  Teresa.Mendez@EJEMPLO.es ").estado()).isEqualTo(202);
        assertThat(buzon.enviadosA(TERESA)).hasSize(1);
    }

    @Test
    @DisplayName("[002][FR-003][SC-007][US1-AC3] un email inexistente recibe la misma respuesta y no se envía nada")
    void emailInexistenteIndistinguible() {
        Respuesta existente = cliente().pedirEnlace("eleva", TERESA);
        Respuesta inexistente = cliente().pedirEnlace("eleva", NADIE);

        assertThat(inexistente.estado()).isEqualTo(existente.estado()).isEqualTo(202);
        assertThat(inexistente.texto()).isEqualTo(existente.texto());
        assertThat(inexistente.tipoContenido()).isEqualTo(existente.tipoContenido());
        assertThat(inexistente.cabeceras().firstValue("Cache-Control"))
                .isEqualTo(existente.cabeceras().firstValue("Cache-Control"));
        assertThat(buzon.enviadosA(NADIE)).isEmpty();
        assertThat(buzon.enviados()).hasSize(1);
    }

    @Test
    @DisplayName("[002][FR-002] un paciente sin email no recibe enlace y un email mal formado responde EMAIL_NO_VALIDO")
    void sinEmailYMalFormado() {
        assertThat(cliente().pedirEnlace("eleva", "gonzalo.cruz@ejemplo.es").estado()).isEqualTo(202);
        assertThat(buzon.enviados()).isEmpty();

        Respuesta mal = cliente().pedirEnlace("eleva", "gonzalo");
        assertThat(mal.estado()).isEqualTo(400);
        assertThat(mal.codigo()).isEqualTo("EMAIL_NO_VALIDO");
        assertThat(mal.mensaje()).isEqualTo("Escribe un email válido, por ejemplo nombre@correo.es.");
    }

    @Test
    @DisplayName("[002][FR-006][SC-009][US1-AC4] un enlace usado, caducado a los 10 minutos o de otra clínica no es válido")
    void enlaceUsadoCaducadoOAjeno() {
        cliente().pedirEnlace("eleva", TERESA);
        String usado = buzon.secreto(buzon.ultimoEnlace(TERESA));
        assertThat(cliente().abrirSesionPortal("eleva", usado).estado()).isEqualTo(204);
        Respuesta reutilizado = cliente().abrirSesionPortal("eleva", usado);
        assertThat(reutilizado.estado()).isEqualTo(401);
        assertThat(reutilizado.codigo()).isEqualTo("ENLACE_NO_VALIDO");
        assertThat(reutilizado.mensaje()).isEqualTo("Este enlace ya no es válido. Pide uno nuevo.");

        cliente().pedirEnlace("eleva", TERESA);
        String caducado = buzon.secreto(buzon.ultimoEnlace(TERESA));
        reloj.avanzar(Duration.ofMinutes(10));
        assertThat(cliente().abrirSesionPortal("eleva", caducado).codigo()).isEqualTo("ENLACE_NO_VALIDO");

        cliente().pedirEnlace("eleva", TERESA);
        String deEleva = buzon.secreto(buzon.ultimoEnlace(TERESA));
        assertThat(cliente().abrirSesionPortal("otra", deEleva).codigo()).isEqualTo("ENLACE_NO_VALIDO");
        assertThat(cliente().abrirSesionPortal("eleva", "inventado").codigo()).isEqualTo("ENLACE_NO_VALIDO");
    }

    @Test
    @DisplayName("[002][FR-006] pedir un enlace nuevo no invalida una sesión ya abierta")
    void enlaceNuevoNoCierraSesion() {
        ClienteApi teresa = clienteEnPortal("eleva", TERESA);
        cliente().pedirEnlace("eleva", TERESA);
        assertThat(teresa.get("/api/portal/citas").estado()).isEqualTo(200);
    }

    @Test
    @DisplayName("[002][FR-006][SC-009] dos aperturas simultáneas del mismo enlace abren exactamente una sesión")
    void aperturasSimultaneas() throws Exception {
        try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
            for (int repeticion = 0; repeticion < 20; repeticion++) {
                buzon.vaciar();
                jdbc.update("DELETE FROM solicitud_enlace_portal");
                cliente().pedirEnlace("eleva", TERESA);
                String secreto = buzon.secreto(buzon.ultimoEnlace(TERESA));
                CountDownLatch salida = new CountDownLatch(1);
                List<Future<Integer>> resultados = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    ClienteApi dispositivo = cliente();
                    resultados.add(hilos.submit(() -> {
                        salida.await();
                        return dispositivo.abrirSesionPortal("eleva", secreto).estado();
                    }));
                }
                salida.countDown();
                List<Integer> estados = new ArrayList<>();
                for (Future<Integer> r : resultados) {
                    estados.add(r.get());
                }
                assertThat(estados).containsExactlyInAnyOrder(204, 401);
            }
        }
    }

    @Test
    @DisplayName("[002][FR-004][US1-AC7] la 6.ª solicitud en una hora responde ENLACES_AGOTADOS, exista o no el email")
    void limiteDeSolicitudes() {
        for (String email : List.of(TERESA, NADIE)) {
            for (int i = 0; i < 5; i++) {
                assertThat(cliente().pedirEnlace("eleva", email).estado()).isEqualTo(202);
            }
            Respuesta sexta = cliente().pedirEnlace("eleva", email);
            assertThat(sexta.estado()).isEqualTo(429);
            assertThat(sexta.codigo()).isEqualTo("ENLACES_AGOTADOS");
            assertThat(sexta.mensaje()).isEqualTo("Has pedido demasiados enlaces. Espera unos minutos y vuelve a probar.");
        }
        assertThat(buzon.enviadosA(TERESA)).hasSize(5);

        reloj.avanzar(Duration.ofMinutes(59));
        assertThat(cliente().pedirEnlace("eleva", TERESA).estado()).isEqualTo(429);
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(cliente().pedirEnlace("eleva", TERESA).estado()).isEqualTo(202);
    }

    @Test
    @DisplayName("[002][FR-004] 10 solicitudes simultáneas para el mismo email dejan exactamente 5 aceptadas")
    void limiteConSolicitudesSimultaneas() throws Exception {
        try (ExecutorService hilos = Executors.newFixedThreadPool(10)) {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                ClienteApi dispositivo = cliente();
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return dispositivo.pedirEnlace("eleva", TERESA).estado();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> r : resultados) {
                estados.add(r.get());
            }
            assertThat(estados).filteredOn(e -> e == 202).hasSize(5);
            assertThat(estados).filteredOn(e -> e == 429).hasSize(5);
        }
    }

    @Test
    @DisplayName("[002][FR-005][US1-AC5] la sesión del portal caduca 30 minutos después de entrar, aunque haya actividad")
    void caducidadAbsoluta() {
        ClienteApi teresa = clienteEnPortal("eleva", TERESA);
        reloj.avanzar(Duration.ofMinutes(20));
        assertThat(teresa.get("/api/portal/citas").estado()).isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(10));

        Respuesta caducada = teresa.get("/api/portal/citas");
        assertThat(caducada.estado()).isEqualTo(401);
        assertThat(caducada.codigo()).isEqualTo("SESION_PORTAL_CADUCADA");
        assertThat(caducada.mensaje()).isEqualTo("Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.");
        assertThat(cliente().get("/api/portal/citas").codigo()).isEqualTo("SIN_SESION_PORTAL");
    }

    @Test
    @DisplayName("[002][US1-AC6] «Salir» cierra la sesión y las respuestas del portal no se guardan en caché")
    void salir() {
        ClienteApi teresa = clienteEnPortal("eleva", TERESA);
        Respuesta citas = teresa.get("/api/portal/citas");
        assertThat(citas.cabeceras().firstValue("Cache-Control")).contains("no-store");

        assertThat(teresa.delete("/api/portal/sesion").estado()).isEqualTo(204);
        assertThat(teresa.get("/api/portal/citas").estado()).isEqualTo(401);
        assertThat(cliente().delete("/api/portal/sesion").estado()).isEqualTo(204);
    }

    @Test
    @DisplayName("[002][FR-029] la sesión del portal no abre el panel, la del panel no abre el portal y no conviven")
    void separacionPanelPortal() {
        ClienteApi teresa = clienteEnPortal("eleva", TERESA);
        assertThat(teresa.get("/api/sesion").estado()).isEqualTo(401);
        assertThat(teresa.get("/api/agenda?profesionalId=" + p.e().maria()).estado()).isEqualTo(401);

        ClienteApi recepcion = clienteConSesion("eleva", DatosPrueba.CLAVE_ELEVA);
        Respuesta desdePanel = recepcion.get("/api/portal/citas");
        assertThat(desdePanel.estado()).isEqualTo(401);
        assertThat(desdePanel.codigo()).isEqualTo("SIN_SESION_PORTAL");

        // Abrir el portal en el mismo navegador cierra la sesión del panel.
        recepcion.pedirEnlace("eleva", TERESA);
        assertThat(recepcion.abrirSesionPortal("eleva", buzon.secreto(buzon.ultimoEnlace(TERESA))).estado()).isEqualTo(204);
        assertThat(recepcion.get("/api/sesion").estado()).isEqualTo(401);
        assertThat(recepcion.get("/api/portal/citas").estado()).isEqualTo(200);
    }

    @Test
    @DisplayName("[002][FR-002] el portal solo acepta JSON y una clínica inexistente responde 404")
    void soloJsonYClinicaInexistente() {
        assertThat(cliente().postCrudo("/api/portal/eleva/enlaces", "text/plain", TERESA).estado()).isEqualTo(415);
        assertThat(cliente().post("/api/portal/inexistente/enlaces", Map.of("email", TERESA)).estado()).isEqualTo(404);
    }
}
