package es.citaclara.cita;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Siempre filtrado por la clínica de la sesión (FR-003, research D7). */
public interface RepositorioCita extends JpaRepository<Cita, Long> {

    @Query("""
            select c from Cita c join fetch c.profesional join fetch c.servicio join fetch c.paciente
            where c.id = :id and c.clinicaId = :clinicaId
            """)
    Optional<Cita> buscar(@Param("id") Long id, @Param("clinicaId") Long clinicaId);

    /** Citas de un profesional cuyo inicio cae en [desde, hasta), en cualquier estado. */
    @Query("""
            select c from Cita c join fetch c.profesional join fetch c.servicio join fetch c.paciente
            where c.profesional.id = :profesionalId and c.inicio >= :desde and c.inicio < :hasta
            order by c.inicio, c.id
            """)
    List<Cita> delProfesionalEntre(@Param("profesionalId") Long profesionalId,
                                   @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Citas ocupantes (reservadas o completadas) del profesional que se solapan con [inicio, fin). */
    @Query("""
            select c from Cita c join fetch c.profesional join fetch c.servicio join fetch c.paciente
            where c.profesional.id = :profesionalId
              and c.estado in (es.citaclara.cita.EstadoCita.RESERVADA, es.citaclara.cita.EstadoCita.COMPLETADA)
              and c.inicio < :fin and c.fin > :inicio
            order by c.inicio
            """)
    List<Cita> ocupantesSolapadas(@Param("profesionalId") Long profesionalId,
                                  @Param("inicio") Instant inicio, @Param("fin") Instant fin);
}
