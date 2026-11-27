package es.citaclara.portal;

import es.citaclara.cita.EstadoCita;
import es.citaclara.semilla.CatalogoDemostracion;
import es.citaclara.semilla.CitaSemilla;
import es.citaclara.semilla.GeneradorSemilla;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-030 y puerta de merge 5: las citas por paciente de specs/002-portal-paciente/datos-demostracion.md
 * se reproducen con el generador puro de la semilla de 001, sin modificarlo. Para regenerar el
 * documento tras un cambio deliberado de la semilla:
 * {@code ./mvnw test -Dtest=DatosPortalDocumentadosTest -DactualizarDatosDemostracion=true}
 */
class DatosPortalDocumentadosTest {

    private static final Path DOCUMENTO = Path.of("..", "specs", "002-portal-paciente", "datos-demostracion.md");
    private static final String INICIO = "<!-- portal:inicio -->";
    private static final String FIN = "<!-- portal:fin -->";
    private static final List<String> PACIENTES = List.of("Teresa Méndez", "Laura Romero", "Rocío Garrido", "Gonzalo Cruz");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Test
    @DisplayName("[002][FR-030] las citas por paciente documentadas para el portal coinciden con la semilla")
    void citasPorPacienteDocumentadas() throws IOException {
        String calculado = tablas();

        if (Boolean.getBoolean("actualizarDatosDemostracion")) {
            String actual = Files.readString(DOCUMENTO, StandardCharsets.UTF_8);
            Files.writeString(DOCUMENTO, actual.substring(0, actual.indexOf(INICIO) + INICIO.length()) + "\n"
                    + calculado + actual.substring(actual.indexOf(FIN)), StandardCharsets.UTF_8);
        }
        String documento = Files.readString(DOCUMENTO, StandardCharsets.UTF_8);
        String documentado = documento.substring(documento.indexOf(INICIO) + INICIO.length(), documento.indexOf(FIN)).strip();
        assertThat(documentado).as("datos-demostracion.md de 002 no coincide con la semilla").isEqualTo(calculado.strip());
        assertThat(documento).doesNotContainPattern("\\b20\\d\\d-\\d\\d-\\d\\d\\b");
    }

    private static String tablas() {
        List<CitaSemilla> todas = GeneradorSemilla.generar();
        StringBuilder md = new StringBuilder();
        md.append("### Resumen por paciente\n\n")
                .append("| Paciente | Email | Historia | Completadas | No asistidas | Canceladas | Importe completado | Futuro (reservadas) | Importe futuro |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (String nombre : PACIENTES) {
            int indice = indicePaciente(nombre);
            List<CitaSemilla> suyas = todas.stream().filter(c -> c.paciente() == indice).toList();
            List<CitaSemilla> historia = suyas.stream().filter(c -> c.posicion() < 0).toList();
            List<CitaSemilla> futuro = suyas.stream().filter(c -> c.posicion() > 0).toList();
            md.append("| ").append(nombre)
                    .append(" | ").append(CatalogoDemostracion.PACIENTES.get(indice).email())
                    .append(" | ").append(historia.size())
                    .append(" | ").append(contar(historia, EstadoCita.COMPLETADA))
                    .append(" | ").append(contar(historia, EstadoCita.NO_ASISTIDA))
                    .append(" | ").append(contar(historia, EstadoCita.CANCELADA))
                    .append(" | ").append(euros(importe(historia.stream().filter(c -> c.estado() == EstadoCita.COMPLETADA).toList())))
                    .append(" | ").append(futuro.size())
                    .append(" | ").append(euros(importe(futuro)))
                    .append(" |\n");
        }

        md.append("\n### Primera cita futura y última cita pasada\n\n")
                .append("| Paciente | Cita | Día laborable | Profesional | Horario | Servicio | Importe | Estado en el portal |\n")
                .append("|---|---|---|---|---|---|---:|---|\n");
        Comparator<CitaSemilla> orden = Comparator.comparingInt(CitaSemilla::posicion).thenComparing(CitaSemilla::inicio);
        for (String nombre : PACIENTES) {
            int indice = indicePaciente(nombre);
            List<CitaSemilla> suyas = todas.stream().filter(c -> c.paciente() == indice).sorted(orden).toList();
            suyas.stream().filter(c -> c.posicion() > 0).findFirst()
                    .ifPresent(c -> md.append(filaEjemplo(nombre, "Primera futura", c)));
            suyas.stream().filter(c -> c.posicion() < 0).reduce((a, b) -> b)
                    .ifPresent(c -> md.append(filaEjemplo(nombre, "Última pasada", c)));
        }
        return md.toString();
    }

    private static String filaEjemplo(String paciente, String cual, CitaSemilla c) {
        CatalogoDemostracion.ServicioDemo servicio = CatalogoDemostracion.SERVICIOS.get(c.servicio());
        String horario = HORA.format(c.inicio()) + "–" + HORA.format(c.inicio().plusMinutes(servicio.minutos()));
        String estado = switch (c.estado()) {
            case RESERVADA -> "Reservada";
            case COMPLETADA -> "Realizada";
            case NO_ASISTIDA -> "No acudiste";
            case CANCELADA -> "Cancelada";
        };
        return "| " + paciente + " | " + cual + " | " + (c.posicion() > 0 ? "+" : "−") + Math.abs(c.posicion())
                + " | " + CatalogoDemostracion.PROFESIONALES.get(c.profesional()).nombre() + " | " + horario
                + " | " + servicio.nombre() + " | " + euros(servicio.precioCentimos()) + " | " + estado + " |\n";
    }

    private static int indicePaciente(String nombre) {
        for (int i = 0; i < CatalogoDemostracion.PACIENTES.size(); i++) {
            if (CatalogoDemostracion.PACIENTES.get(i).nombre().equals(nombre)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Paciente no está en la semilla: " + nombre);
    }

    private static long contar(List<CitaSemilla> citas, EstadoCita estado) {
        return citas.stream().filter(c -> c.estado() == estado).count();
    }

    private static long importe(List<CitaSemilla> citas) {
        return citas.stream().mapToLong(c -> CatalogoDemostracion.SERVICIOS.get(c.servicio()).precioCentimos()).sum();
    }

    /** Mismo criterio que el formateador único (CLDR es-ES): miles solo a partir de 5 cifras enteras. */
    private static String euros(long centimos) {
        String entero = Long.toString(centimos / 100);
        if (entero.length() >= 5) {
            StringBuilder agrupado = new StringBuilder();
            for (int i = 0; i < entero.length(); i++) {
                if (i > 0 && (entero.length() - i) % 3 == 0) {
                    agrupado.append('.');
                }
                agrupado.append(entero.charAt(i));
            }
            entero = agrupado.toString();
        }
        return entero + "," + String.format("%02d", centimos % 100) + " € (" + centimos + " céntimos)";
    }
}
