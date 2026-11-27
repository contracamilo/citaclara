package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-012: inventario de datos personales y de salud con retención y derechos. */
class InventarioDatosTest {

    @Test
    @DisplayName("[005][FR-012][SC-005][US4-AC1][US4-AC3] el inventario trae todas las columnas, los datos de pacientes y la referencia de 5 años sin alterar importes")
    void inventario() {
        String s = LectorInforme.informe().seccion("Inventario de datos personales");
        assertThat(s).contains("| Dato | Ubicación | Acceso | Protección | Conservación | Derechos |");
        assertThat(s).contains("Nombre del paciente").contains("Teléfono del paciente").contains("Email del paciente")
                .contains("Citas y especialidad").contains("Clave de clínica");
        for (String fila : s.split("\n")) {
            if (fila.startsWith("|") && !fila.startsWith("|---") && !fila.contains("| Dato |")) {
                for (String celda : fila.substring(1).split("\\|")) {
                    assertThat(celda.strip()).as(fila).isNotBlank();
                }
            }
        }
        assertThat(s).contains("5 años desde la última cita").contains("anonimiza").contains("importes y totales históricos");
    }
}
