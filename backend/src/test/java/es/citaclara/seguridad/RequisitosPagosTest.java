package es.citaclara.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 005, FR-010 y FR-011: lista de Stripe/PCI con veredictos y requisitos RP-nnn. */
class RequisitosPagosTest {

    private final LectorInforme pagos = LectorInforme.pagos();

    @Test
    @DisplayName("[005][FR-010][SC-004][US2-AC1] cada punto de la lista de Stripe y PCI tiene un veredicto válido")
    void listaConVeredicto() {
        String lista = pagos.seccion("Lista de comprobación");
        List<String> filas = filasDeTabla(lista);
        assertThat(filas).hasSizeGreaterThanOrEqualTo(8);
        for (String fila : filas) {
            String[] c = fila.split("\\|", -1);
            assertThat(c[2].strip()).as(c[1]).isIn("se cumple", "no se cumple", "no aplica todavía");
            assertThat(c[3].strip()).as(c[1]).isNotBlank();
        }
    }

    @Test
    @DisplayName("[005][FR-011][US2-AC2][US2-AC3] los requisitos RP-nnn son únicos y cubren los controles obligatorios y los dos orígenes de pago")
    void requisitos() {
        List<String> filas = filasDeTabla(pagos.seccion("Requisitos para quien construya los cobros"));
        Set<String> ids = new HashSet<>();
        List<String> textos = new ArrayList<>();
        for (String fila : filas) {
            String[] c = fila.split("\\|", -1);
            assertThat(c[1].strip()).matches("RP-\\d{3}");
            assertThat(ids.add(c[1].strip())).as("único " + c[1]).isTrue();
            assertThat(c[3].strip()).isIn("recepcion", "paciente en línea", "ambos");
            assertThat(c[4].strip()).isNotBlank();
            textos.add(c[2].toLowerCase());
        }
        String todo = String.join("\n", textos);
        assertThat(todo).contains("tarjeta").contains("firma").contains("sin modificar").contains("idempotente")
                .contains("céntimos").contains("euros").contains("restringidas").contains("fuera del código")
                .contains("al céntimo").contains("no adivinable").contains("un solo uso").contains("caducidad");
        assertThat(filas.stream().map(f -> f.split("\\|", -1)[3].strip()).toList())
                .contains("recepcion", "paciente en línea");
    }

    private static List<String> filasDeTabla(String seccion) {
        List<String> filas = new ArrayList<>();
        for (String linea : seccion.split("\n")) {
            if (linea.startsWith("|") && !linea.startsWith("|---") && !linea.matches("^\\|\\s*(Punto|Id)\\s*\\|.*")) {
                filas.add(linea);
            }
        }
        return filas;
    }
}
