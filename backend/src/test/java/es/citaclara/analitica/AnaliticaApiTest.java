package es.citaclara.analitica;

import es.citaclara.semilla.CargadorSemilla;
import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba.Escenario;
import es.citaclara.soporte.DatosPrueba.OtraClinica;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;

import static es.citaclara.soporte.DatosPrueba.CLAVE_ELEVA;
import static es.citaclara.soporte.DatosPrueba.CLAVE_OTRA;
import static org.assertj.core.api.Assertions.assertThat;

/** Reloj por defecto: miércoles 25/11/2026 → la última semana completa es 16/11–22/11. */
class AnaliticaApiTest extends PruebaIntegracionBase {

    @Autowired
    private CargadorSemilla cargador;

    private static final LocalDate LUNES_ULTIMA = LocalDate.of(2026, 11, 16);

    @Test
    @DisplayName("[004][FR-001][US4-AC1] sin sesión responde 401 SIN_SESION y no devuelve datos")
    void sinSesion() {
        datos.escenario();
        Respuesta r = cliente().get("/api/analitica");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SIN_SESION");
        assertThat(r.cuerpo().has("evolucion")).isFalse();
    }

    @Test
    @DisplayName("[004][FR-001][US4-AC1] con la sesión caducada (más de 8 h) responde 401 SESION_CADUCADA")
    void sesionCaducada() {
        datos.escenario();
        ClienteApi recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
        assertThat(recepcion.get("/api/analitica").estado()).isEqualTo(200);
        reloj.avanzar(Duration.ofHours(8).plusMinutes(1));
        Respuesta r = recepcion.get("/api/analitica");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SESION_CADUCADA");
    }

    @Test
    @DisplayName("[004][FR-002][US4-AC2] con la clave de una clínica no aparece nada de otra y sus cifras no la incluyen")
    void aislamiento() {
        Escenario e = datos.escenario();
        OtraClinica otra = datos.otraClinica();
        datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), LUNES_ULTIMA, "10:00", 45, 4000, "COMPLETADA");
        datos.cita(otra.clinica(), otra.profesional(), otra.servicio(), otra.paciente(),
                es.citaclara.soporte.DatosPrueba.instante(LUNES_ULTIMA, "10:00"),
                es.citaclara.soporte.DatosPrueba.instante(LUNES_ULTIMA, "10:45"), 9900, "COMPLETADA");

        JsonNode eleva = clienteConSesion("eleva", CLAVE_ELEVA).get("/api/analitica").cuerpo();
        assertThat(eleva.path("ultimaSemana").path("profesionales").findValuesAsString("nombre"))
                .containsExactly("Jorge", "Lucía", "María");
        assertThat(eleva.path("ingresos").path("totalCentimos").asLong()).isEqualTo(4000);

        JsonNode deOtra = clienteConSesion("otra", CLAVE_OTRA).get("/api/analitica").cuerpo();
        assertThat(deOtra.path("ultimaSemana").path("profesionales").findValuesAsString("nombre")).containsExactly("Elena");
        assertThat(deOtra.path("ingresos").path("totalCentimos").asLong()).isEqualTo(9900);
        assertThat(deOtra.path("ingresos").path("servicios")).hasSize(1);
    }

    @Test
    @DisplayName("[004][FR-011][US4-AC4] una clínica sin citas devuelve sinDatos=true, todo a cero y todos los profesionales con «Sin datos»")
    void clinicaVacia() {
        datos.escenario();
        JsonNode r = clienteConSesion("eleva", CLAVE_ELEVA).get("/api/analitica").cuerpo();
        assertThat(r.path("sinDatos").asBoolean()).isTrue();
        assertThat(r.path("ingresos").path("totalCentimos").asLong()).isZero();
        JsonNode profesionales = r.path("ultimaSemana").path("profesionales");
        assertThat(profesionales).hasSize(3);
        for (JsonNode p : profesionales) {
            assertThat(p.path("tasaNoAsistencia").path("decimas").isNull()).isTrue();
            assertThat(p.path("ocupacion").path("decimas").asInt()).isZero();
        }
    }

    @Test
    @DisplayName("[004][FR-007][US3-AC2] las 8 semanas siempre están presentes y en orden, también sin citas")
    void ochoSemanasSiempre() {
        datos.escenario();
        JsonNode evolucion = clienteConSesion("eleva", CLAVE_ELEVA).get("/api/analitica").cuerpo().path("evolucion");
        assertThat(evolucion).hasSize(8);
        assertThat(evolucion.get(0).path("semana").path("inicio").asString()).isEqualTo("2026-09-28");
        assertThat(evolucion.get(7).path("semana").path("fin").asString()).isEqualTo("2026-11-22");
        assertThat(evolucion.get(3).path("tasaNoAsistencia").path("decimas").isNull()).isTrue();
    }

    @Test
    @DisplayName("[004][FR-008][US1-AC2] una cita reservada con inicio en la última semana no cuenta en ninguna cifra")
    void reservadaNoCuenta() {
        Escenario e = datos.escenario();
        datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), LUNES_ULTIMA.plusDays(1), "10:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), LUNES_ULTIMA.plusDays(2), "10:00", 45, 4000, "COMPLETADA");
        JsonNode r = clienteConSesion("eleva", CLAVE_ELEVA).get("/api/analitica").cuerpo();
        JsonNode maria = null;
        for (JsonNode p : r.path("ultimaSemana").path("profesionales")) {
            if ("María".equals(p.path("nombre").asString())) maria = p;
        }
        assertThat(maria).isNotNull();
        assertThat(maria.path("tasaNoAsistencia").path("total").asInt()).isEqualTo(1);
        assertThat(maria.path("ocupacion").path("parte").asInt()).isEqualTo(45);
        assertThat(r.path("ingresos").path("totalCentimos").asLong()).isEqualTo(4000);
    }

    private String instantanea() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM cita) || '|' || (SELECT coalesce(sum(importe_centimos), 0) FROM cita)
                  || '|' || (SELECT coalesce(max(version), 0) FROM cita) || '|' || (SELECT count(*) FROM paciente)
                  || '|' || (SELECT count(*) FROM profesional) || '|' || (SELECT count(*) FROM servicio)
                  || '|' || (SELECT count(*) FROM clinica) || '|' || (SELECT count(*) FROM horario_atencion)
                  || '|' || (SELECT coalesce(sum(extract(epoch FROM inicio)), 0) FROM cita)
                """, String.class);
    }

    @Test
    @DisplayName("[004][FR-003][SC-005][US4-AC3] usar la analítica no modifica ningún dato")
    void soloLectura() {
        cargador.cargar();
        ClienteApi recepcion = clienteConSesion("eleva", "eleva-demo-2026");
        String antes = instantanea();
        for (int i = 0; i < 3; i++) {
            assertThat(recepcion.get("/api/analitica").estado()).isEqualTo(200);
        }
        assertThat(instantanea()).isEqualTo(antes);
    }

    @Test
    @DisplayName("[004][FR-003] RepositorioAnalitica no es CrudRepository ni JpaRepository (sin save ni delete)")
    void repositorioSoloLectura() {
        assertThat(CrudRepository.class.isAssignableFrom(RepositorioAnalitica.class)).isFalse();
        assertThat(JpaRepository.class.isAssignableFrom(RepositorioAnalitica.class)).isFalse();
    }

    @Test
    @DisplayName("[004][FR-003] la analítica no expone ninguna operación de escritura")
    void sinEscritura() {
        datos.escenario();
        ClienteApi recepcion = clienteConSesion("eleva", CLAVE_ELEVA);
        assertThat(recepcion.post("/api/analitica", java.util.Map.of()).estado()).isBetween(400, 499);
        assertThat(recepcion.delete("/api/analitica").estado()).isBetween(400, 499);
    }
}
