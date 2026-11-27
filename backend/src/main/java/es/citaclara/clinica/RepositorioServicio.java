package es.citaclara.clinica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Siempre filtrado por la clínica de la sesión (FR-003, research D7). */
public interface RepositorioServicio extends JpaRepository<Servicio, Long> {

    Optional<Servicio> findByIdAndClinicaId(Long id, Long clinicaId);

    List<Servicio> findByClinicaIdOrderByEspecialidadAscNombreAsc(Long clinicaId);
}
