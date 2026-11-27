package es.citaclara.analitica;

import es.citaclara.cita.Cita;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repositorio de solo lectura (FR-003, research D6): extiende {@link Repository}, NO
 * {@code JpaRepository}, así que no expone {@code save} ni {@code delete}. Siempre filtrado por la
 * clínica de la sesión (FR-002).
 */
public interface RepositorioAnalitica extends Repository<Cita, Long> {

    /** Citas cerradas con inicio en [desde, hasta). Las reservadas no se cargan. */
    @Query("""
            select new es.citaclara.analitica.CitaCerrada(c.profesional.id, c.servicio.id, c.inicio, c.fin,
                                                          c.importeCentimos, c.estado)
            from Cita c
            where c.clinicaId = :clinicaId and c.inicio >= :desde and c.inicio < :hasta
              and c.estado in (es.citaclara.cita.EstadoCita.COMPLETADA,
                               es.citaclara.cita.EstadoCita.NO_ASISTIDA,
                               es.citaclara.cita.EstadoCita.CANCELADA)
            order by c.inicio, c.id
            """)
    List<CitaCerrada> cerradasEntre(@Param("clinicaId") Long clinicaId,
                                    @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
