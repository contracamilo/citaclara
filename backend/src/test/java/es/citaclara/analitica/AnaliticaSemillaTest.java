package es.citaclara.analitica;

import es.citaclara.semilla.CargadorSemilla;
import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cifras de «Cifras de la semilla» de specs/004-panel-analitica/spec.md. Con el reloj en lunes
 * 30/11/2026 las 40 jornadas de la historia (−40 a −1) son exactamente las 8 semanas completas
 * (research D9). Orden del API: Jorge, Lucía, María (por nombre) y servicios por especialidad y nombre.
 */
class AnaliticaSemillaTest extends PruebaIntegracionBase {

    @Autowired
    private CargadorSemilla cargador;

    private JsonNode analitica(ZonedDateTime hoy) {
        reloj.fijar(hoy);
        cargador.cargar();
        ClienteApi recepcion = clienteConSesion("eleva", "eleva-demo-2026");
        return recepcion.get("/api/analitica").cuerpo();
    }

    private JsonNode analiticaEnLunes() {
        return analitica(ZonedDateTime.of(2026, 11, 30, 12, 0, 0, 0, DatosPrueba.MADRID));
    }

    private static JsonNode porNombre(JsonNode lista, String nombre) {
        for (JsonNode n : lista) {
            if (nombre.equals(n.path("nombre").asString())) {
                return n;
            }
        }
        throw new AssertionError("No hay " + nombre);
    }

    @Test
    @DisplayName("[004][FR-015][SC-001][US1-AC2] tasa de no asistencia de la última semana: María 1 de 39, Jorge 5 de 41, Lucía 4 de 55")
    void tasaUltimaSemana() {
        JsonNode ultima = analiticaEnLunes().path("ultimaSemana");
        assertThat(ultima.path("semana").path("inicio").asString()).isEqualTo("2026-11-23");
        assertThat(ultima.path("semana").path("fin").asString()).isEqualTo("2026-11-29");
        JsonNode profesionales = ultima.path("profesionales");
        assertThat(profesionales.findValuesAsString("nombre")).containsExactly("Jorge", "Lucía", "María");

        record Esperada(String nombre, int parte, int total, int decimas) { }
        for (Esperada e : List.of(new Esperada("María", 1, 39, 26), new Esperada("Jorge", 5, 41, 122), new Esperada("Lucía", 4, 55, 73))) {
            JsonNode tasa = porNombre(profesionales, e.nombre()).path("tasaNoAsistencia");
            assertThat(tasa.path("parte").asInt()).as(e.nombre()).isEqualTo(e.parte());
            assertThat(tasa.path("total").asInt()).as(e.nombre()).isEqualTo(e.total());
            assertThat(tasa.path("decimas").asInt()).as(e.nombre()).isEqualTo(e.decimas());
        }
    }

    @Test
    @DisplayName("[004][FR-015][SC-001][US1-AC3] ocupación de la última semana: María 1.800 min, Jorge 1.590, Lucía 1.605 sobre 3.300")
    void ocupacionUltimaSemana() {
        JsonNode profesionales = analiticaEnLunes().path("ultimaSemana").path("profesionales");
        record Esperada(String nombre, int minutos, int decimas) { }
        for (Esperada e : List.of(new Esperada("María", 1800, 545), new Esperada("Jorge", 1590, 482), new Esperada("Lucía", 1605, 486))) {
            JsonNode ocupacion = porNombre(profesionales, e.nombre()).path("ocupacion");
            assertThat(ocupacion.path("parte").asInt()).as(e.nombre()).isEqualTo(e.minutos());
            assertThat(ocupacion.path("total").asInt()).as(e.nombre()).isEqualTo(3300);
            assertThat(ocupacion.path("decimas").asInt()).as(e.nombre()).isEqualTo(e.decimas());
        }
    }

    @Test
    @DisplayName("[004][FR-015][SC-001][SC-002][US2-AC1] ingresos por servicio de la semilla: total 34.870,00 € (3487000 céntimos)")
    void ingresosPorServicio() {
        JsonNode ingresos = analiticaEnLunes().path("ingresos");
        record Esperado(String nombre, int citas, long centimos) { }
        List<Esperado> esperados = List.of(
                new Esperado("Primera visita fisioterapia", 97, 485000), new Esperado("Sesión fisioterapia", 426, 1704000),
                new Esperado("Consulta nutrición", 277, 969500), new Esperado("Primera visita nutrición", 73, 328500));
        assertThat(ingresos.path("servicios").findValuesAsString("nombre"))
                .containsExactlyElementsOf(esperados.stream().map(Esperado::nombre).toList());
        long suma = 0;
        for (Esperado e : esperados) {
            JsonNode s = porNombre(ingresos.path("servicios"), e.nombre());
            assertThat(s.path("citasCompletadas").asInt()).as(e.nombre()).isEqualTo(e.citas());
            assertThat(s.path("importeCentimos").asLong()).as(e.nombre()).isEqualTo(e.centimos());
            suma += s.path("importeCentimos").asLong();
        }
        assertThat(ingresos.path("totalCentimos").asLong()).isEqualTo(3487000).isEqualTo(suma);
    }

    @Test
    @DisplayName("[004][FR-016][SC-002][US2-AC5] ingresos no obtenidos de la semilla: total 4275,00 € (427500 céntimos)")
    void noObtenidos() {
        JsonNode ingresos = analiticaEnLunes().path("ingresos");
        record Esperado(String nombre, int citas, long centimos) { }
        long suma = 0;
        for (Esperado e : List.of(new Esperado("Sesión fisioterapia", 56, 224000), new Esperado("Primera visita fisioterapia", 11, 55000),
                new Esperado("Consulta nutrición", 27, 94500), new Esperado("Primera visita nutrición", 12, 54000))) {
            JsonNode s = porNombre(ingresos.path("servicios"), e.nombre());
            assertThat(s.path("citasNoAsistidas").asInt()).as(e.nombre()).isEqualTo(e.citas());
            assertThat(s.path("importeNoObtenidoCentimos").asLong()).as(e.nombre()).isEqualTo(e.centimos());
            suma += s.path("importeNoObtenidoCentimos").asLong();
        }
        assertThat(ingresos.path("totalNoObtenidoCentimos").asLong()).isEqualTo(427500).isEqualTo(suma);
    }

    @Test
    @DisplayName("[004][FR-015][SC-001][SC-002][US3-AC1] evolución de la semilla: 8 semanas con recuentos, tasa e ingresos")
    void evolucion() {
        JsonNode evolucion = analiticaEnLunes().path("evolucion");
        int[] completadas = {110, 115, 105, 100, 114, 102, 109, 118};
        int[] noAsistidas = {11, 10, 18, 12, 10, 19, 16, 10};
        int[] canceladas = {14, 11, 9, 13, 10, 12, 9, 7};
        int[] tasa = {81, 74, 136, 96, 75, 143, 119, 74};
        long[] ingresos = {438000, 461000, 421000, 399000, 455000, 405000, 433000, 475000};
        assertThat(evolucion).hasSize(8);
        assertThat(evolucion.get(0).path("semana").path("inicio").asString()).isEqualTo("2026-10-05");
        long suma = 0;
        for (int i = 0; i < 8; i++) {
            JsonNode s = evolucion.get(i);
            assertThat(s.path("completadas").asInt()).as("semana " + (i + 1)).isEqualTo(completadas[i]);
            assertThat(s.path("noAsistidas").asInt()).as("semana " + (i + 1)).isEqualTo(noAsistidas[i]);
            assertThat(s.path("canceladas").asInt()).as("semana " + (i + 1)).isEqualTo(canceladas[i]);
            assertThat(s.path("tasaNoAsistencia").path("decimas").asInt()).as("semana " + (i + 1)).isEqualTo(tasa[i]);
            assertThat(s.path("ingresosCentimos").asLong()).as("semana " + (i + 1)).isEqualTo(ingresos[i]);
            suma += s.path("ingresosCentimos").asLong();
        }
        assertThat(suma).isEqualTo(3487000);
    }

    @Test
    @DisplayName("[004][FR-015][US3-AC1] ocupación semanal por profesional de la semilla")
    void ocupacionSemanal() {
        JsonNode evolucion = analiticaEnLunes().path("evolucion");
        // Orden del API: Jorge, Lucía, María.
        int[][] esperadas = {
                {509, 527, 432, 427, 445, 355, 505, 482},   // Jorge
                {418, 468, 405, 405, 477, 445, 409, 486},   // Lucía
                {468, 473, 509, 436, 518, 477, 464, 545}};  // María
        for (int p = 0; p < 3; p++) {
            List<Integer> obtenidas = new ArrayList<>();
            for (JsonNode semana : evolucion) {
                obtenidas.add(semana.path("ocupacion").get(p).path("ocupacion").path("decimas").asInt());
            }
            assertThat(obtenidas).as("profesional " + p).containsExactly(
                    java.util.Arrays.stream(esperadas[p]).boxed().toArray(Integer[]::new));
        }
    }

    @Test
    @DisplayName("[004][FR-010][US3-AC2] con la semilla cargada en miércoles los totales siguen cuadrando y la semana más antigua tiene menos citas")
    void cargadaEnMiercoles() {
        JsonNode analitica = analitica(ZonedDateTime.of(2026, 12, 2, 12, 0, 0, 0, DatosPrueba.MADRID));
        JsonNode evolucion = analitica.path("evolucion");
        long sumaSemanas = 0;
        int cerradasPrimera = 0;
        int cerradasSegunda = 0;
        for (int i = 0; i < 8; i++) {
            JsonNode s = evolucion.get(i);
            sumaSemanas += s.path("ingresosCentimos").asLong();
            int cerradas = s.path("completadas").asInt() + s.path("noAsistidas").asInt() + s.path("canceladas").asInt();
            if (i == 0) cerradasPrimera = cerradas;
            if (i == 1) cerradasSegunda = cerradas;
        }
        long sumaServicios = 0;
        for (JsonNode s : analitica.path("ingresos").path("servicios")) {
            sumaServicios += s.path("importeCentimos").asLong();
        }
        assertThat(sumaSemanas).isEqualTo(sumaServicios).isEqualTo(analitica.path("ingresos").path("totalCentimos").asLong());
        assertThat(cerradasPrimera).isLessThan(cerradasSegunda);
        assertThat(evolucion.get(0).path("semana").path("inicio").asString()).isEqualTo(LocalDate.of(2026, 10, 5).toString());
    }
}
