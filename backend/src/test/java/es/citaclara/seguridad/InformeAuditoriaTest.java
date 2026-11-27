package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005: el informe respeta el formato de contracts/informe-auditoria.md. */
class InformeAuditoriaTest {

    private static final Set<String> VEREDICTOS = Set.of("cumple", "cumple con reservas", "no cumple", "no aplica");

    private final LectorInforme informe = LectorInforme.informe();

    @Test
    @DisplayName("[005][FR-001][FR-018][SC-001][US1-AC1] la cabecera trae commit, fecha, edición OWASP, alcance, método y herramientas")
    void cabeceraReproducible() {
        assertThat(informe.cabecera("Commit auditado")).matches("[0-9a-f]{7,40}");
        assertThat(informe.cabecera("Fecha")).matches("\\d{2}/\\d{2}/\\d{4}");
        assertThat(informe.cabecera("Edición OWASP")).isEqualTo("OWASP Top 10:2025");
        assertThat(informe.cabecera("Alcance")).isNotBlank();
        assertThat(informe.cabecera("Método")).isNotBlank();
        assertThat(informe.cabecera("Herramientas")).containsPattern("\\d+\\.\\d+");
    }

    @Test
    @DisplayName("[005][FR-001][SC-001][US1-AC1] hay exactamente diez veredictos A01 a A10, cada uno válido y los «no aplica» justificados")
    void diezVeredictos() {
        List<LectorInforme.Veredicto> filas = informe.veredictos();
        assertThat(filas).hasSize(10);
        for (int i = 0; i < 10; i++) {
            assertThat(filas.get(i).categoria()).startsWith(String.format("A%02d", i + 1));
            assertThat(VEREDICTOS).contains(filas.get(i).veredicto());
            assertThat(filas.get(i).hallazgos()).isNotBlank();
            if (filas.get(i).veredicto().equals("no aplica")) {
                assertThat(filas.get(i).evidencia()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("[005][FR-001][FR-018][US1-AC1] las secciones fijas del informe existen")
    void seccionesFijas() {
        for (String s : List.of("Inventario de datos personales", "Resultado de las herramientas automáticas",
                "Requisitos para otras features", "Reauditoría", "Plan de seguimiento")) {
            assertThat(informe.tieneSeccion(s)).as(s).isTrue();
        }
    }

    @Test
    @DisplayName("[005][FR-006][FR-009][SC-001] el informe evalúa la configuración del entorno (A02) y la resistencia a abuso (A10) con hallazgos concretos")
    void configuracionYAbuso() {
        List<LectorInforme.Veredicto> filas = informe.veredictos();
        assertThat(filas.get(1).categoria()).contains("A02");
        assertThat(filas.get(1).hallazgos()).contains("H-0");
        assertThat(filas.get(9).categoria()).contains("A10");
        assertThat(filas.get(9).hallazgos()).contains("H-16");
        assertThat(informe.texto()).contains("limit_req").contains("COOKIE_SEGURA").contains("DB_CLAVE");
    }
}
