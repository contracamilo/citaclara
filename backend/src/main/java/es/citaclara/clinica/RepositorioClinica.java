package es.citaclara.clinica;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RepositorioClinica extends JpaRepository<Clinica, Long> {

    Optional<Clinica> findByIdentificador(String identificador);

    /**
     * Bloquea la fila de la clínica hasta el fin de la transacción (002, research R3): serializa el
     * recuento y la inserción de solicitudes de enlace para que el límite de FR-004 no se pueda saltar
     * con peticiones simultáneas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clinica c where c.identificador = :identificador")
    Optional<Clinica> bloquearPorIdentificador(@Param("identificador") String identificador);
}
