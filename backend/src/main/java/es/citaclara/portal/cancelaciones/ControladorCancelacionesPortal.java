package es.citaclara.portal.cancelaciones;

import es.citaclara.acceso.SesionClinica;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aviso de cancelaciones del portal en el panel de recepción (US4). Protegido por la sesión del panel. */
@RestController
@RequestMapping("/api/cancelaciones-portal")
public class ControladorCancelacionesPortal {

    private final ServicioCancelacionesPortal servicio;

    public ControladorCancelacionesPortal(ServicioCancelacionesPortal servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ServicioCancelacionesPortal.CancelacionesPortalRespuesta pendientes(
            @RequestAttribute(SesionClinica.CLINICA) Long clinicaId) {
        return servicio.pendientes(clinicaId);
    }

    @PostMapping("/{citaId}/revision")
    public ResponseEntity<Void> revisar(@RequestAttribute(SesionClinica.CLINICA) Long clinicaId,
                                        @PathVariable long citaId) {
        servicio.revisar(clinicaId, citaId);
        return ResponseEntity.noContent().build();
    }
}
