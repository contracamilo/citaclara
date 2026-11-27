package es.citaclara.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RepositorioRecordatorio extends JpaRepository<Recordatorio, Long> {

    /**
     * Registra el recordatorio de una cita si aún no existe (research D3). Devuelve 1 si lo ha
     * insertado y 0 si ya existía; ante dos inserciones simultáneas la segunda espera a la primera.
     */
    @Modifying
    @Query(value = """
            INSERT INTO recordatorio (clinica_id, cita_id, email_destino, enlace_huella, generado_en)
            VALUES (:clinicaId, :citaId, :email, :huella, :generadoEn)
            ON CONFLICT (cita_id) DO NOTHING
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("clinicaId") Long clinicaId, @Param("citaId") Long citaId,
                           @Param("email") String email, @Param("huella") String huella,
                           @Param("generadoEn") Instant generadoEn);

    Optional<Recordatorio> findByCitaId(Long citaId);

    Optional<Recordatorio> findByEnlaceHuella(String huella);
}
