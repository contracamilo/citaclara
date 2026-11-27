package es.citaclara.analitica;

import es.citaclara.acceso.SesionClinica;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/analitica (specs/004-panel-analitica/contracts/api.yaml). Única operación: solo lectura. */
@RestController
public class ControladorAnalitica {

    private final ServicioAnalitica analitica;

    public ControladorAnalitica(ServicioAnalitica analitica) {
        this.analitica = analitica;
    }

    @GetMapping("/api/analitica")
    public AnaliticaRespuesta analitica(@RequestAttribute(SesionClinica.CLINICA) Long clinicaId) {
        return analitica.analitica(clinicaId);
    }
}
