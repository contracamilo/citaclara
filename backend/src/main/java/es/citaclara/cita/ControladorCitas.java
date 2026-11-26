package es.citaclara.cita;

import es.citaclara.acceso.SesionClinica;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControladorCitas {

    private final ServicioAltaCita alta;
    private final ServicioEstadoCita estados;

    public ControladorCitas(ServicioAltaCita alta, ServicioEstadoCita estados) {
        this.alta = alta;
        this.estados = estados;
    }

    public record CambioEstadoPeticion(@NotNull(message = "Elige una acción.") Accion accion,
                                       @NotNull(message = "Recarga la agenda e inténtalo de nuevo.") Integer version) {
    }

    @PostMapping("/api/citas")
    public ResponseEntity<CitaRespuesta> crear(@RequestAttribute(SesionClinica.CLINICA) Long clinicaId,
                                               @Valid @RequestBody NuevaCitaPeticion peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alta.crear(clinicaId, peticion));
    }

    @PostMapping("/api/citas/{citaId}/estado")
    public CitaRespuesta cambiarEstado(@RequestAttribute(SesionClinica.CLINICA) Long clinicaId,
                                       @PathVariable Long citaId,
                                       @Valid @RequestBody CambioEstadoPeticion peticion) {
        return estados.cambiar(clinicaId, citaId, peticion.accion(), peticion.version());
    }
}
