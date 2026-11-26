package es.citaclara.paciente;

import es.citaclara.acceso.SesionClinica;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ControladorPacientes {

    private final ServicioPacientes pacientes;

    public ControladorPacientes(ServicioPacientes pacientes) {
        this.pacientes = pacientes;
    }

    @GetMapping("/api/pacientes")
    public List<PacienteRespuesta> buscar(@RequestAttribute(SesionClinica.CLINICA) Long clinicaId,
                                          @RequestParam(defaultValue = "") String q) {
        return pacientes.buscar(clinicaId, q);
    }
}
