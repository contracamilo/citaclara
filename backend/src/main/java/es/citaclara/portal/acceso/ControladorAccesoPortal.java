package es.citaclara.portal.acceso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Acceso al portal del paciente (US1, contracts/api.yaml). */
@RestController
@RequestMapping("/api/portal")
public class ControladorAccesoPortal {

    private final ServicioEnlaces enlaces;

    public ControladorAccesoPortal(ServicioEnlaces enlaces) {
        this.enlaces = enlaces;
    }

    /** {@code email} admite nulo aquí: la validación la hace {@link EmailPortal} con EMAIL_NO_VALIDO. */
    public record PeticionEnlace(String email) {
    }

    public record PeticionSesion(@NotBlank(message = "Este enlace ya no es válido. Pide uno nuevo.") String enlace) {
    }

    @PostMapping("/{identificador}/enlaces")
    public ResponseEntity<Void> pedirEnlace(@PathVariable String identificador, @RequestBody PeticionEnlace cuerpo) {
        enlaces.pedir(identificador, cuerpo.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/{identificador}/sesion")
    public ResponseEntity<Void> abrirSesion(@PathVariable String identificador,
                                            @Valid @RequestBody PeticionSesion cuerpo,
                                            HttpServletRequest peticion) {
        enlaces.consumir(identificador, cuerpo.enlace(), peticion);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/sesion")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest peticion) {
        enlaces.cerrar(peticion);
        return ResponseEntity.noContent().build();
    }
}
