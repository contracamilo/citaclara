package es.citaclara.clinica;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Siempre filtrado por la clínica de la sesión (FR-003, research D7). */
public interface RepositorioProfesional extends JpaRepository<Profesional, Long> {

    Optional<Profesional> findByIdAndClinicaId(Long id, Long clinicaId);

    List<Profesional> findByClinicaIdOrderByNombre(Long clinicaId);

    /**
     * Bloquea la fila del profesional hasta el fin de la transacción. Serializa las altas
     * simultáneas del mismo profesional para que la comprobación previa vea a la ganadora y no se
     * produzcan interbloqueos en la exclusion constraint; la garantía sigue siendo la restricción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profesional p where p.id = :id and p.clinicaId = :clinicaId")
    Optional<Profesional> bloquear(@Param("id") Long id, @Param("clinicaId") Long clinicaId);
}
