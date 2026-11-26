package es.citaclara.semilla;

import es.citaclara.acceso.ServicioAcceso;
import es.citaclara.comun.Telefono;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static es.citaclara.soporte.DatosPrueba.HOY;
import static org.assertj.core.api.Assertions.assertThat;

/** Semilla cargada con el reloj en el miércoles 25/11/2026 a las 12:00. */
class SemillaTest extends PruebaIntegracionBase {

    @Autowired
    private CargadorSemilla cargador;

    private Timestamp inicioDeHoy;
    private Timestamp inicioDeManana;

    @BeforeEach
    void cargar() {
        cargador.cargar();
        inicioDeHoy = Timestamp.from(ZonaClinica.inicioDelDia(HOY));
        inicioDeManana = Timestamp.from(ZonaClinica.inicioDelDia(HOY.plusDays(1)));
    }

    @Test
    @DisplayName("[001][FR-030][US4-AC1] existe Clínica Eleva con su clave, horario, 3 profesionales, 4 servicios y 40 pacientes")
    void catalogo() {
        Map<String, Object> clinica = jdbc.queryForMap("SELECT * FROM clinica WHERE identificador = 'eleva'");
        assertThat(clinica.get("nombre")).isEqualTo("Clínica Eleva");
        assertThat(ServicioAcceso.coincide("eleva-demo-2026", (String) clinica.get("clave_hash"))).isTrue();

        assertThat(jdbc.queryForList(
                "SELECT dia_semana || ' ' || apertura || '-' || cierre FROM horario_atencion ORDER BY dia_semana", String.class))
                .containsExactly("1 09:00:00-20:00:00", "2 09:00:00-20:00:00", "3 09:00:00-20:00:00",
                        "4 09:00:00-20:00:00", "5 09:00:00-20:00:00");
        assertThat(jdbc.queryForList("SELECT nombre || ' (' || especialidad || ')' FROM profesional ORDER BY id", String.class))
                .containsExactly("María (fisioterapia)", "Jorge (fisioterapia)", "Lucía (nutricion)");
        assertThat(jdbc.queryForList(
                "SELECT nombre || ' ' || duracion_minutos || ' ' || precio_centimos FROM servicio ORDER BY id", String.class))
                .containsExactly("Sesión fisioterapia 45 4000", "Primera visita fisioterapia 60 5000",
                        "Consulta nutrición 30 3500", "Primera visita nutrición 45 4500");

        List<Map<String, Object>> pacientes = jdbc.queryForList("SELECT nombre, telefono, email FROM paciente");
        assertThat(pacientes).hasSize(40);
        assertThat(pacientes).allSatisfy(p -> {
            assertThat((String) p.get("nombre")).isNotBlank();
            assertThat(Telefono.normalizar((String) p.get("telefono"))).contains((String) p.get("telefono"));
            assertThat((String) p.get("email")).matches("[a-z0-9.]+@ejemplo\\.es");
        });
        assertThat(pacientes.stream().map(p -> p.get("telefono")).distinct()).hasSize(40);
    }

    @Test
    @DisplayName("[001][FR-031][SC-008][US4-AC2] la historia está cerrada, con 9–11 % de no asistidas y 7–9 % de canceladas")
    void historia() {
        int total = cuenta("inicio < ?", inicioDeHoy);
        assertThat(total).isGreaterThan(300);
        assertThat(cuenta("inicio < ? AND estado = 'RESERVADA'", inicioDeHoy)).isZero();
        double noAsistidas = cuenta("inicio < ? AND estado = 'NO_ASISTIDA'", inicioDeHoy) * 100.0 / total;
        double canceladas = cuenta("inicio < ? AND estado = 'CANCELADA'", inicioDeHoy) * 100.0 / total;
        assertThat(noAsistidas).isBetween(9.0, 11.0);
        assertThat(canceladas).isBetween(7.0, 9.0);
        List<LocalDate> dias = fechas("inicio < ?", inicioDeHoy);
        assertThat(dias).hasSize(40);
        assertThat(dias.getFirst()).isEqualTo(DiasLaborables.fecha(HOY, -40));
        assertThat(dias.getLast()).isEqualTo(DiasLaborables.fecha(HOY, -1));
    }

    @Test
    @DisplayName("[001][FR-031][US4-AC3] los 10 días laborables futuros tienen citas reservadas de los tres profesionales y huecos libres")
    void futuro() {
        assertThat(cuenta("inicio >= ? AND estado <> 'RESERVADA'", inicioDeManana)).isZero();
        List<LocalDate> dias = fechas("inicio >= ?", inicioDeManana);
        assertThat(dias).hasSize(10);
        assertThat(dias.getFirst()).isEqualTo(DiasLaborables.fecha(HOY, 1));
        assertThat(dias.getLast()).isEqualTo(DiasLaborables.fecha(HOY, 10));
        // Cada profesional tiene citas cada día futuro y deja al menos una hora libre de las 11 de horario.
        List<Integer> minutosOcupados = jdbc.queryForList("""
                SELECT (sum(extract(epoch FROM fin - inicio)) / 60)::int FROM cita
                WHERE inicio >= ? GROUP BY profesional_id, (inicio AT TIME ZONE 'Europe/Madrid')::date
                """, Integer.class, inicioDeManana);
        assertThat(minutosOcupados).hasSize(30).allMatch(m -> m <= 10 * 60);
    }

    @Test
    @DisplayName("[001][FR-032][SC-001][US4-AC4] la semilla no tiene solapes y respeta el horario; sin citas el día de carga")
    void reglas() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM cita a JOIN cita b ON a.profesional_id = b.profesional_id AND a.id < b.id
                 AND a.estado IN ('RESERVADA','COMPLETADA') AND b.estado IN ('RESERVADA','COMPLETADA')
                 AND a.inicio < b.fin AND b.inicio < a.fin
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM cita WHERE (inicio AT TIME ZONE 'Europe/Madrid')::time < '09:00'
                   OR (fin AT TIME ZONE 'Europe/Madrid')::time > '20:00'
                   OR (inicio AT TIME ZONE 'Europe/Madrid')::date <> (fin AT TIME ZONE 'Europe/Madrid')::date
                   OR extract(isodow FROM inicio AT TIME ZONE 'Europe/Madrid') > 5
                   OR extract(minute FROM inicio AT TIME ZONE 'Europe/Madrid')::int % 5 <> 0
                """, Integer.class)).isZero();
        assertThat(cuenta("inicio >= ? AND inicio < ?", inicioDeHoy, inicioDeManana)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM cita c JOIN servicio s ON s.id = c.servicio_id
                JOIN profesional p ON p.id = c.profesional_id
                WHERE c.importe_centimos <> s.precio_centimos OR p.especialidad <> s.especialidad
                   OR c.fin <> c.inicio + make_interval(mins => s.duracion_minutos)
                """, Integer.class)).isZero();
    }

    private int cuenta(String condicion, Object... parametros) {
        return jdbc.queryForObject("SELECT count(*) FROM cita WHERE " + condicion, Integer.class, parametros);
    }

    private List<LocalDate> fechas(String condicion, Timestamp parametro) {
        return jdbc.queryForList("SELECT DISTINCT (inicio AT TIME ZONE 'Europe/Madrid')::date AS d FROM cita WHERE "
                        + condicion + " ORDER BY d", Date.class, parametro).stream().map(Date::toLocalDate).toList();
    }
}
