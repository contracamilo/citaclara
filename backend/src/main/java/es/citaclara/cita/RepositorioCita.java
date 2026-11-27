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

    /**
     * 002 (portal del paciente, FR-028): citas de las fichas de la clínica cuyo email normalizado
     * coincide con el de la sesión del portal. Solo lectura.
     */
    @Query("""
            select c from Cita c join fetch c.profesional join fetch c.servicio join fetch c.paciente p
            where c.clinicaId = :clinicaId and p.email is not null and lower(trim(p.email)) = :email
            order by c.inicio, c.id
            """)
    List<Cita> delPortal(@Param("clinicaId") Long clinicaId, @Param("email") String email);

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

    /**
     * Citas reservadas con inicio en [desde, hasta), con paciente con email y sin recordatorio aún
     * (003, research D4). Mide en instantes: el cambio de horario no desplaza ni duplica citas.
     */
    @Query("""
            select c from Cita c join fetch c.profesional join fetch c.servicio join fetch c.paciente p
            where c.estado = es.citaclara.cita.EstadoCita.RESERVADA
              and c.inicio >= :desde and c.inicio < :hasta
              and p.email is not null and length(trim(p.email)) > 0
              and not exists (select 1 from es.citaclara.recordatorio.Recordatorio r where r.citaId = c.id)
            order by c.inicio, c.id
            """)
    List<Cita> paraRecordatorio(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Cita de un enlace de cancelación, por la huella del recordatorio (003, research D5). */
    @Query("""
            select c from Cita c join fetch c.profesional join fetch c.servicio join fetch c.paciente
            where c.id = (select r.citaId from es.citaclara.recordatorio.Recordatorio r where r.enlaceHuella = :huella)
            """)
    Optional<Cita> buscarPorHuella(@Param("huella") String huella);
}
