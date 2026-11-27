package es.citaclara.portal.cancelaciones;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Cancelaciones hechas desde el portal (data-model «CancelacionPortal», FR-017, FR-019–FR-022).
 * Siempre filtrado por clínica.
 */
@Repository
public class RepositorioCancelaciones {

    private final JdbcTemplate jdbc;

    public RepositorioCancelaciones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(long citaId, long clinicaId, Instant canceladaEn) {
        jdbc.update("INSERT INTO cancelacion_portal (cita_id, clinica_id, cancelada_en) VALUES (?, ?, ?)",
                citaId, clinicaId, Timestamp.from(canceladaEn));
    }

    public boolean existe(long citaId, long clinicaId) {
        Boolean existe = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM cancelacion_portal WHERE cita_id = ? AND clinica_id = ?)",
                Boolean.class, citaId, clinicaId);
        return Boolean.TRUE.equals(existe);
    }

    /** Pendientes de revisar, de la más reciente a la más antigua (FR-020). */
    public List<Pendiente> pendientes(long clinicaId) {
        return jdbc.query("""
                SELECT cp.cita_id, cp.cancelada_en, c.inicio, c.fin, pa.nombre AS paciente,
                       pr.nombre AS profesional, s.nombre AS servicio
                FROM cancelacion_portal cp
                JOIN cita c ON c.id = cp.cita_id AND c.clinica_id = cp.clinica_id
                JOIN paciente pa ON pa.id = c.paciente_id
                JOIN profesional pr ON pr.id = c.profesional_id
                JOIN servicio s ON s.id = c.servicio_id
                WHERE cp.clinica_id = ? AND cp.revisada_en IS NULL
                ORDER BY cp.cancelada_en DESC, cp.cita_id DESC
                """, (fila, n) -> new Pendiente(
                fila.getLong("cita_id"),
                fila.getTimestamp("cancelada_en").toInstant(),
                fila.getTimestamp("inicio").toInstant(),
                fila.getTimestamp("fin").toInstant(),
                fila.getString("paciente"),
                fila.getString("profesional"),
                fila.getString("servicio")), clinicaId);
    }

    /** Idempotente (FR-021): solo la primera revisión fija {@code revisada_en}. Devuelve las filas cambiadas. */
    public int marcarRevisada(long citaId, long clinicaId, Instant ahora) {
        return jdbc.update("""
                UPDATE cancelacion_portal SET revisada_en = ?
                WHERE cita_id = ? AND clinica_id = ? AND revisada_en IS NULL
                """, Timestamp.from(ahora), citaId, clinicaId);
    }

    public record Pendiente(long citaId, Instant canceladaEn, Instant inicio, Instant fin,
                            String paciente, String profesional, String servicio) {
    }
}
