package es.citaclara.recordatorio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operaciones públicas de cancelación por el paciente (contracts/api.yaml). Sin sesión de recepción:
 * la autoridad es el enlace. El {@code GET} solo consulta; únicamente el {@code POST} cancela.
 */
@RestController
@RequestMapping("/api/cancelacion")
public class ControladorCancelacionPaciente {

    private final ServicioCancelacionPaciente servicio;

    public ControladorCancelacionPaciente(ServicioCancelacionPaciente servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{enlace}")
    public CancelacionRespuesta consultar(@PathVariable String enlace) {
        return servicio.consultar(enlace);
    }

    @PostMapping("/{enlace}")
    public CancelacionRespuesta cancelar(@PathVariable String enlace,
                                         @RequestBody(required = false) Map<String, Object> cuerpo) {
        return servicio.cancelar(enlace);
    }
}
