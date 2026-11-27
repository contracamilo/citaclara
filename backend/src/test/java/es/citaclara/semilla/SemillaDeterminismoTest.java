package es.citaclara.semilla;

import es.citaclara.soporte.DatosPrueba;
import es.citaclara.soporte.PruebaIntegracionBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SemillaDeterminismoTest extends PruebaIntegracionBase {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 11, 25);

    @Autowired
    private CargadorSemilla cargador;

    @Test
    @DisplayName("[001][FR-029][SC-008][US4-AC5] dos cargas el mismo día producen exactamente los mismos datos")
    void mismoDia() {
        List<String> primera = cargarEn(MIERCOLES, LocalTime.of(9, 0));
        List<String> segunda = cargarEn(MIERCOLES, LocalTime.of(18, 30));
        assertThat(segunda).isEqualTo(primera);
        assertThat(fichas()).hasSize(40);
    }

    @Test
    @DisplayName("[001][FR-029][SC-008][US4-AC5] cargas en lunes y en miércoles coinciden por posición relativa; solo cambian las fechas")
    void diasDeLaSemanaDistintos() {
        List<String> lunes = cargarEn(LUNES, LocalTime.of(12, 0));
        List<Map<String, Object>> fichasLunes = fichas();
        List<String> miercoles = cargarEn(MIERCOLES, LocalTime.of(12, 0));

        assertThat(miercoles).isEqualTo(lunes);
        assertThat(fichas()).isEqualTo(fichasLunes);
        assertThat(primeraFecha()).isEqualTo(DiasLaborables.fecha(MIERCOLES, -40));
    }

    @Test
    @DisplayName("[001][FR-029] recargar borra y recrea Clínica Eleva sin tocar otras clínicas")
    void idempotenteYAislada() {
        datos.otraClinica();
        cargarEn(MIERCOLES, LocalTime.of(12, 0));
        cargarEn(MIERCOLES, LocalTime.of(12, 0));
        assertThat(jdbc.queryForList("SELECT identificador FROM clinica ORDER BY identificador", String.class))
                .containsExactly("eleva", "otra");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente p JOIN clinica c ON c.id = p.clinica_id "
                + "WHERE c.identificador = 'otra'", Integer.class)).isEqualTo(1);
    }

    /** Carga con el reloj en ese día y devuelve las citas como (posición, profesional, horas, paciente, servicio, importe, estado). */
    private List<String> cargarEn(LocalDate dia, LocalTime hora) {
        reloj.fijar(ZonedDateTime.of(dia, hora, DatosPrueba.MADRID));
        cargador.cargar();
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT (c.inicio AT TIME ZONE 'Europe/Madrid')::date AS fecha,
                       to_char(c.inicio AT TIME ZONE 'Europe/Madrid', 'HH24:MI') AS inicio,
                       to_char(c.fin AT TIME ZONE 'Europe/Madrid', 'HH24:MI') AS fin,
                       p.nombre AS profesional, pa.nombre AS paciente, s.nombre AS servicio,
                       c.importe_centimos AS importe, c.estado
                FROM cita c JOIN profesional p ON p.id = c.profesional_id
                JOIN paciente pa ON pa.id = c.paciente_id JOIN servicio s ON s.id = c.servicio_id
                ORDER BY c.inicio, p.nombre
                """);
        return filas.stream().map(f -> DiasLaborables.posicion(dia, ((java.sql.Date) f.get("fecha")).toLocalDate())
                + " " + f.get("profesional") + " " + f.get("inicio") + "-" + f.get("fin") + " " + f.get("paciente")
                + " " + f.get("servicio") + " " + f.get("importe") + " " + f.get("estado")).toList();
    }

    private List<Map<String, Object>> fichas() {
        return jdbc.queryForList("SELECT nombre, telefono, email FROM paciente ORDER BY nombre");
    }

    private LocalDate primeraFecha() {
        return jdbc.queryForObject("SELECT min((inicio AT TIME ZONE 'Europe/Madrid')::date) FROM cita", java.sql.Date.class)
                .toLocalDate();
    }
}
