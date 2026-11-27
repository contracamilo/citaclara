package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005: reglas de las fichas de hallazgo (contracts/informe-auditoria.md) y FR-020, FR-021, FR-022. */
class FichasHallazgoTest {

    private static final Pattern ID_TEST = Pattern.compile("\\[005\\](?:\\[[A-Za-z0-9-]+\\])+");
    private static final Set<String> CLAVES = Set.of("Categoría", "Severidad", "Datos afectados", "Parte",
            "Verificación", "Mejora", "Esfuerzo", "Criterio de aceptación", "Estado", "Toca");

    private final LectorInforme informe = LectorInforme.informe();
    private final List<LectorInforme.Hallazgo> fichas = informe.hallazgos();

    @Test
    @DisplayName("[005][FR-002][SC-002][US1-AC2] los identificadores H-nn son únicos y consecutivos, y cada ficha tiene todos sus campos")
    void identificadoresYCampos() {
        assertThat(fichas).isNotEmpty();
        for (int i = 0; i < fichas.size(); i++) {
            assertThat(fichas.get(i).id()).isEqualTo(String.format("H-%02d", i + 1));
            assertThat(fichas.get(i).campos().keySet()).containsAll(CLAVES);
            assertThat(fichas.get(i).campo("Severidad")).isIn("crítica", "alta", "media", "baja");
            assertThat(fichas.get(i).campo("Esfuerzo")).isIn("S", "M", "L");
            assertThat(fichas.get(i).campo("Estado")).matches("(abierto|corregido|derivado)\\b.*");
        }
    }

    @Test
    @DisplayName("[005][FR-002][FR-013][SC-002][US1-AC3] los hallazgos críticos y altos traen mejora y criterio de aceptación")
    void gravesConMejora() {
        for (LectorInforme.Hallazgo h : fichas) {
            if (grave(h)) {
                assertThat(h.campo("Mejora")).as(h.id()).isNotBlank();
                assertThat(h.campo("Criterio de aceptación")).as(h.id()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("[005][FR-013][US3-AC1] un hallazgo derivado cita su solicitud en specs/MAPA.md y esa solicitud existe")
    void derivadosEnElMapa() throws IOException {
        String mapa = Files.readString(LectorInforme.RAIZ.resolve("specs/MAPA.md"));
        for (LectorInforme.Hallazgo h : fichas) {
            if (h.campo("Estado").startsWith("derivado")) {
                Matcher m = Pattern.compile("S-\\d{3}").matcher(h.campo("Estado"));
                assertThat(m.find()).as(h.id() + " cita una solicitud S-nnn").isTrue();
                assertThat(mapa).as(h.id()).contains("| " + m.group() + " |");
            }
        }
    }

    @Test
    @DisplayName("[005][FR-020][SC-002][US3-AC1] un hallazgo corregido cita una prueba [005]… que existe en el repositorio")
    void corregidosConPrueba() throws IOException {
        String pruebas = textoDePruebas();
        for (LectorInforme.Hallazgo h : fichas) {
            if (h.campo("Estado").startsWith("corregido")) {
                Matcher m = ID_TEST.matcher(h.campo("Estado"));
                assertThat(m.find()).as(h.id() + " cita su prueba").isTrue();
                assertThat(pruebas).as(h.id()).contains(m.group());
            }
        }
    }

    @Test
    @DisplayName("[005][FR-002][SC-002][US1-AC3] ninguna ficha contiene secretos ni datos personales reales")
    void sinSecretos() {
        String texto = informe.texto();
        assertThat(texto).doesNotContainPattern("sk_(live|test)_[A-Za-z0-9]{8,}");
        assertThat(texto).doesNotContainPattern("eleva-demo-\\d{4}");
        assertThat(texto).doesNotContainPattern("\\+34\\s?\\d{9}");
        assertThat(texto).doesNotContainPattern("[\\w.]+@[\\w.]+\\.(com|es)");
    }

    @Test
    @DisplayName("[005][FR-021][FR-022][SC-008][US1-AC3] los hallazgos de usuarios individuales y de límite de intentos están corregidos o derivados, y ningún grave queda abierto sin gestionar")
    void incondicional() {
        for (LectorInforme.Hallazgo h : fichas) {
            if (grave(h)) {
                assertThat(h.campo("Estado")).as(h.id()).doesNotStartWith("abierto");
            }
        }
        List<String> titulos = fichas.stream().map(LectorInforme.Hallazgo::titulo).collect(Collectors.toList());
        assertThat(titulos).anyMatch(t -> t.toLowerCase().contains("límite de intentos"));
        assertThat(titulos).anyMatch(t -> t.toLowerCase().contains("clave única compartida"));
    }

    private static boolean grave(LectorInforme.Hallazgo h) {
        return h.campo("Severidad").equals("crítica") || h.campo("Severidad").equals("alta");
    }

    private static String textoDePruebas() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String carpeta : List.of("backend/src/test", "frontend/tests", "frontend/src")) {
            Path dir = LectorInforme.RAIZ.resolve(carpeta);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path f : s.filter(Files::isRegularFile).toList()) {
                    if (f.toString().matches(".*\\.(java|ts|tsx)$")) {
                        sb.append(Files.readString(f)).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }
}
