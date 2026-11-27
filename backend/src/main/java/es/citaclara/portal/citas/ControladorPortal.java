package es.citaclara.portal.citas;

import es.citaclara.portal.acceso.SesionPortal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Citas del paciente (US2) y su cancelación (US3). La sesión la valida {@code InterceptorPortal}. */
@RestController
@RequestMapping("/api/portal/citas")
public class ControladorPortal {

    private final ServicioMisCitas misCitas;
    private final ServicioCancelacionPortal cancelacion;

    public ControladorPortal(ServicioMisCitas misCitas, ServicioCancelacionPortal cancelacion) {
        this.misCitas = misCitas;
        this.cancelacion = cancelacion;
    }

    public record PeticionCancelacion(@NotNull(message = "Recarga la página e inténtalo de nuevo.") Integer version) {
    }

    @GetMapping
    public MisCitas misCitas(@RequestAttribute(SesionPortal.CLINICA) Long clinicaId,
                             @RequestAttribute(SesionPortal.EMAIL) String email) {
        return misCitas.de(clinicaId, email);
    }

    @PostMapping("/{citaId}/cancelacion")
    public CitaPortal cancelar(@RequestAttribute(SesionPortal.CLINICA) Long clinicaId,
                               @RequestAttribute(SesionPortal.EMAIL) String email,
                               @PathVariable long citaId,
                               @Valid @RequestBody PeticionCancelacion cuerpo) {
        return cancelacion.cancelar(clinicaId, email, citaId, cuerpo.version());
    }
}
