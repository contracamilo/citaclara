package es.citaclara.portal;

import es.citaclara.soporte.ClienteApi;
import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import es.citaclara.soporte.Respuesta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static es.citaclara.soporte.DatosPrueba.HOY;
import static es.citaclara.soporte.DatosPrueba.MADRID;
import static es.citaclara.soporte.DatosPrueba.MANANA;
import static org.assertj.core.api.Assertions.assertThat;

class MisCitasTest extends PruebaIntegracionBase {

    private DatosPrueba.EscenarioPortal p;
    private DatosPrueba.Escenario e;

    @BeforeEach
    void preparar() {
        p = datos.escenarioPortal();
        e = p.e();
    }

    private JsonNode misCitas(String email) {
        Respuesta r = clienteEnPortal("eleva", email).get("/api/portal/citas");
        assertThat(r.estado()).isEqualTo(200);
        return r.cuerpo();
    }

    private static List<String> horas(JsonNode citas) {
        List<String> horas = new ArrayList<>();
        citas.forEach(c -> horas.add(c.path("fecha").asString() + " " + c.path("horaInicio").asString()));
        return horas;
    }

    @Test
    @DisplayName("[002][FR-008][FR-009][US2-AC1] la próxima cita de Teresa aparece con fecha, franja, profesional, servicio, precio y estado")
    void proximaCitaCompleta() {
        datos.cita(e, e.maria(), e.primeraFisio(), p.teresa(), MANANA, "10:15", 60, 5000, "RESERVADA");

        JsonNode cuerpo = misCitas("teresa.mendez@ejemplo.es");

        assertThat(cuerpo.path("clinicaNombre").asString()).isEqualTo("Clínica Eleva");
        assertThat(cuerpo.path("pacientes").get(0).asString()).isEqualTo("Teresa Méndez");
        JsonNode cita = cuerpo.path("proximas").get(0);
        assertThat(cita.path("fecha").asString()).isEqualTo(MANANA.toString());
        assertThat(cita.path("horaInicio").asString()).isEqualTo("10:15");
        assertThat(cita.path("horaFin").asString()).isEqualTo("11:15");
        assertThat(cita.path("profesional").asString()).isEqualTo("María");
        assertThat(cita.path("servicio").asString()).isEqualTo("Primera visita fisioterapia");
        assertThat(cita.path("importeCentimos").asInt()).isEqualTo(5000);
        assertThat(cita.path("estadoPortal").asString()).isEqualTo("RESERVADA");
        assertThat(cita.path("paciente").asString()).isEqualTo("Teresa Méndez");
        assertThat(cita.path("cancelable").asBoolean()).isFalse(); // faltan menos de 24 h (mañana 10:15, ahora 12:00)
        assertThat(cita.path("motivoNoCancelable").asString())
                .isEqualTo("Faltan menos de 24 horas para esta cita. Para cancelarla, llama a la clínica.");
        assertThat(cuerpo.path("pasadas").size()).isZero();
    }

    @Test
    @DisplayName("[002][FR-008][US2-AC2] próximas de la más cercana a la más lejana y pasadas de la más reciente a la más antigua")
    void orden() {
        LocalDate lunes = HOY.plusDays(5);
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), lunes, "12:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), MANANA.plusDays(1), "09:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.jorge(), e.sesionFisio(), p.teresa(), lunes, "10:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.minusDays(7), "10:00", 45, 4000, "COMPLETADA");
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.minusDays(1), "10:00", 45, 4000, "COMPLETADA");

        JsonNode cuerpo = misCitas("teresa.mendez@ejemplo.es");

        assertThat(horas(cuerpo.path("proximas"))).containsExactly(
                MANANA.plusDays(1) + " 09:00", lunes + " 10:00", lunes + " 12:00");
        assertThat(horas(cuerpo.path("pasadas"))).containsExactly(
                HOY.minusDays(1) + " 10:00", HOY.minusDays(7) + " 10:00");
    }

    @Test
    @DisplayName("[002][FR-009][US2-AC3][US2-AC4] completada → Realizada, no asistida → No acudiste, cancelada → Cancelada, sin acciones")
    void estadosDelPaciente() {
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.minusDays(3), "10:00", 45, 4000, "COMPLETADA");
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.minusDays(2), "10:00", 45, 4000, "NO_ASISTIDA");
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.minusDays(1), "10:00", 45, 4000, "CANCELADA");

        JsonNode pasadas = misCitas("teresa.mendez@ejemplo.es").path("pasadas");

        List<String> estados = new ArrayList<>();
        pasadas.forEach(c -> {
            estados.add(c.path("estadoPortal").asString());
            assertThat(c.path("cancelable").asBoolean()).isFalse();
            assertThat(c.path("motivoNoCancelable").isNull()).isTrue();
        });
        assertThat(estados).containsExactly("CANCELADA", "NO_ACUDISTE", "REALIZADA");
    }

    @Test
    @DisplayName("[002][US2-AC5] una cita futura cancelada por recepción aparece en pasadas como Cancelada")
    void futuraCancelada() {
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.plusDays(6), "10:00", 45, 4000, "CANCELADA");

        JsonNode cuerpo = misCitas("teresa.mendez@ejemplo.es");

        assertThat(cuerpo.path("proximas").size()).isZero();
        assertThat(cuerpo.path("pasadas").get(0).path("estadoPortal").asString()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("[002][US2-AC6] una reservada ya iniciada aparece en pasadas como pendiente de cierre y no se puede cancelar")
    void pendienteDeCierre() {
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY, "11:30", 45, 4000, "RESERVADA");

        JsonNode cuerpo = misCitas("teresa.mendez@ejemplo.es");

        assertThat(cuerpo.path("proximas").size()).isZero();
        JsonNode cita = cuerpo.path("pasadas").get(0);
        assertThat(cita.path("estadoPortal").asString()).isEqualTo("PENDIENTE_CIERRE");
        assertThat(cita.path("cancelable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("[002][US2-AC7] sin citas futuras, la lista de próximas está vacía y se conserva el historial")
    void sinProximas() {
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.minusDays(3), "10:00", 45, 4000, "COMPLETADA");

        JsonNode cuerpo = misCitas("teresa.mendez@ejemplo.es");

        assertThat(cuerpo.path("proximas").size()).isZero();
        assertThat(cuerpo.path("pasadas").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("[002][FR-010] el historial no tiene límite de antigüedad")
    void historialCompleto() {
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.minusYears(3), "10:00", 45, 4000, "COMPLETADA");

        assertThat(misCitas("teresa.mendez@ejemplo.es").path("pasadas").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("[002][FR-026] una cita en el día del cambio a horario de verano conserva su hora local")
    void cambioDeHora() {
        reloj.fijar(ZonedDateTime.of(2026, 3, 20, 9, 0, 0, 0, MADRID));
        LocalDate cambio = LocalDate.of(2026, 3, 30); // lunes siguiente al domingo 29/03 (cambio de hora)
        // Cita insertada directamente el mismo domingo del cambio: el portal solo la muestra.
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), LocalDate.of(2026, 3, 29), "10:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), cambio, "10:00", 45, 4000, "RESERVADA");

        JsonNode proximas = misCitas("teresa.mendez@ejemplo.es").path("proximas");

        assertThat(horas(proximas)).containsExactly("2026-03-29 10:00", "2026-03-30 10:00");
        assertThat(proximas.get(0).path("horaFin").asString()).isEqualTo("10:45");
    }

    @Test
    @DisplayName("[002][FR-028] con varias fichas con el mismo email se muestran las citas de todas, con su paciente")
    void variasFichas() {
        datos.cita(e, e.maria(), e.sesionFisio(), p.laura(), HOY.plusDays(6), "10:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.jorge(), e.sesionFisio(), p.hijoLaura(), HOY.plusDays(6), "11:00", 45, 4000, "RESERVADA");

        JsonNode cuerpo = misCitas("laura.romero@ejemplo.es");

        List<String> pacientes = new ArrayList<>();
        cuerpo.path("proximas").forEach(c -> pacientes.add(c.path("paciente").asString()));
        assertThat(pacientes).containsExactly("Laura Romero", "Hijo de Laura");
        List<String> nombres = new ArrayList<>();
        cuerpo.path("pacientes").forEach(n -> nombres.add(n.asString()));
        assertThat(nombres).containsExactlyInAnyOrder("Laura Romero", "Hijo de Laura");
    }

    @Test
    @DisplayName("[002][FR-011][FR-028][SC-004][US2-AC8] Teresa nunca ve citas ni datos de otros pacientes ni de otra clínica")
    void aislamiento() {
        datos.cita(e, e.maria(), e.sesionFisio(), p.teresa(), HOY.plusDays(6), "10:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), p.laura(), HOY.plusDays(6), "11:00", 45, 4000, "RESERVADA");
        datos.cita(e, e.maria(), e.sesionFisio(), e.ana(), HOY.plusDays(6), "12:00", 45, 4000, "RESERVADA");
        DatosPrueba.OtraClinica o = p.otra();
        datos.cita(o.clinica(), o.profesional(), o.servicio(), p.teresaOtraClinica(),
                DatosPrueba.instante(HOY.plusDays(6), "13:00"), DatosPrueba.instante(HOY.plusDays(6), "13:45"),
                4000, "RESERVADA");

        ClienteApi teresa = clienteEnPortal("eleva", "teresa.mendez@ejemplo.es");
        Respuesta r = teresa.get("/api/portal/citas");

        assertThat(horas(r.cuerpo().path("proximas"))).containsExactly(HOY.plusDays(6) + " 10:00");
        assertThat(r.texto()).doesNotContain("Laura", "Ana Pérez", "Elena", "+34", "telefono", "Clínica Otra");
    }
}
