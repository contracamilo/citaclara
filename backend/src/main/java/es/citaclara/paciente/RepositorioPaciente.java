package es.citaclara.paciente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Siempre filtrado por la clínica de la sesión (FR-003, research D7). */
public interface RepositorioPaciente extends JpaRepository<Paciente, Long> {

    Optional<Paciente> findByIdAndClinicaId(Long id, Long clinicaId);

    Optional<Paciente> findByClinicaIdAndTelefono(Long clinicaId, String telefono);

    /**
     * Búsqueda por nombre sin distinguir mayúsculas ni tildes (unaccent) o por cifras del teléfono
     * normalizado; máximo 20 resultados ordenados por nombre (FR-018, research D10).
     */
    @Query(value = """
            SELECT * FROM paciente
            WHERE clinica_id = :clinicaId
              AND (lower(inmutable_unaccent(nombre)) LIKE '%' || lower(inmutable_unaccent(:texto)) || '%' ESCAPE '\\'
                   OR (:cifras <> '' AND telefono LIKE '%' || :cifras || '%'))
            ORDER BY nombre, id
            LIMIT 20
            """, nativeQuery = true)
    List<Paciente> buscar(@Param("clinicaId") Long clinicaId, @Param("texto") String texto,
                          @Param("cifras") String cifras);
}
