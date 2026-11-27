package es.citaclara.acceso;

import es.citaclara.clinica.RepositorioClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ControladorAcceso {

    private final RepositorioClinica clinicas;
    private final ServicioAcceso acceso;

    public ControladorAcceso(RepositorioClinica clinicas, ServicioAcceso acceso) {
        this.clinicas = clinicas;
        this.acceso = acceso;
    }

    public record ClinicaPublica(String nombre) {
    }

    public record PeticionSesion(@NotBlank(message = "Escribe la clave de la clínica.") String clave) {
    }

    /** Datos públicos mínimos para la pantalla de acceso: solo el nombre. */
    @GetMapping("/clinicas/{identificador}")
    public ClinicaPublica clinica(@PathVariable String identificador) {
        return clinicas.findByIdentificador(identificador)
                .map(c -> new ClinicaPublica(c.getNombre()))
                .orElseThrow(ErrorNegocio::noEncontrado);
    }

    @PostMapping("/clinicas/{identificador}/sesion")
    public ResponseEntity<Void> iniciarSesion(@PathVariable String identificador,
                                              @Valid @RequestBody PeticionSesion cuerpo,
                                              HttpServletRequest peticion) {
        acceso.iniciarSesion(identificador, cuerpo.clave(), peticion);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sesion")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest peticion) {
        acceso.cerrarSesion(peticion);
        return ResponseEntity.noContent().build();
    }
}
