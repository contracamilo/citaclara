package es.citaclara.portal.acceso;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Solicitudes de enlace al portal (data-model «SolicitudEnlacePortal», research R1 y R3). Se guarda
 * una fila por solicitud, exista o no el email; {@code token_hash} solo si el email coincide.
 */
@Repository
public class RepositorioSolicitudesEnlace {

    private final JdbcTemplate jdbc;

    public RepositorioSolicitudesEnlace(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int contarDesde(long clinicaId, String email, Instant desde) {
        Integer total = jdbc.queryForObject("""
                SELECT count(*) FROM solicitud_enlace_portal
                WHERE clinica_id = ? AND email_normalizado = ? AND solicitada_en > ?
                """, Integer.class, clinicaId, email, Timestamp.from(desde));
        return total == null ? 0 : total;
    }

    public void insertar(long clinicaId, String email, String tokenHash, Instant solicitadaEn, Instant caducaEn) {
        jdbc.update("""
                INSERT INTO solicitud_enlace_portal (clinica_id, email_normalizado, token_hash, solicitada_en, caduca_en)
                VALUES (?, ?, ?, ?, ?)
                """, clinicaId, email, tokenHash, Timestamp.from(solicitadaEn), Timestamp.from(caducaEn));
    }

    public void borrarAntiguas(long clinicaId, String email, Instant antesDe) {
        jdbc.update("DELETE FROM solicitud_enlace_portal WHERE clinica_id = ? AND email_normalizado = ? AND solicitada_en < ?",
                clinicaId, email, Timestamp.from(antesDe));
    }

    /**
     * Consume el enlace de forma atómica (un solo uso, sin caducar y de esta clínica, FR-006): dos
     * aperturas simultáneas nunca consumen el mismo enlace dos veces.
     */
    public Optional<String> consumir(long clinicaId, String tokenHash, Instant ahora) {
        Timestamp momento = Timestamp.from(ahora);
        List<String> email = jdbc.queryForList("""
                UPDATE solicitud_enlace_portal SET usada_en = ?
                WHERE clinica_id = ? AND token_hash = ? AND usada_en IS NULL AND caduca_en > ?
                RETURNING email_normalizado
                """, String.class, momento, clinicaId, tokenHash, momento);
        return email.stream().findFirst();
    }
}
