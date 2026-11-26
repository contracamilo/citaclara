package es.citaclara.clinica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositorioClinica extends JpaRepository<Clinica, Long> {

    Optional<Clinica> findByIdentificador(String identificador);
}
