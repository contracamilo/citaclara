package es.citaclara.clinica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositorioHorario extends JpaRepository<HorarioAtencion, HorarioAtencion.Clave> {

    Optional<HorarioAtencion> findByClinicaIdAndDiaSemana(Long clinicaId, Short diaSemana);

    List<HorarioAtencion> findByClinicaIdOrderByDiaSemana(Long clinicaId);

    /** Horario de un día ISO (1 = lunes … 7 = domingo), vacío si la clínica no atiende. */
    default Optional<HorarioAtencion> delDia(Long clinicaId, int diaSemana) {
        return findByClinicaIdAndDiaSemana(clinicaId, (short) diaSemana);
    }
}
