package es.citaclara.portal.acceso;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Fichas de una clínica por email normalizado (FR-002, FR-028, research R5), sobre el índice
 * {@code paciente_email_portal}. Siempre filtrado por clínica.
 */
@Repository
public class RepositorioPacientesPortal {

    private final JdbcTemplate jdbc;

    public RepositorioPacientesPortal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeConEmail(long clinicaId, String emailNormalizado) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM paciente
                               WHERE clinica_id = ? AND email IS NOT NULL AND lower(btrim(email)) = ?)
                """, Boolean.class, clinicaId, emailNormalizado);
        return Boolean.TRUE.equals(existe);
    }

    public List<String> nombresConEmail(long clinicaId, String emailNormalizado) {
        return jdbc.queryForList("""
                SELECT nombre FROM paciente
                WHERE clinica_id = ? AND email IS NOT NULL AND lower(btrim(email)) = ?
                ORDER BY nombre, id
                """, String.class, clinicaId, emailNormalizado);
    }
}
