package es.citaclara.agenda;

import es.citaclara.acceso.SesionClinica;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class ControladorAgenda {

    private final ServicioAgenda agenda;

    public ControladorAgenda(ServicioAgenda agenda) {
        this.agenda = agenda;
    }

    /** GET /api/agenda?profesionalId=&fecha= (fecha opcional: hoy en Madrid según el servidor, US1-AC3). */
    @GetMapping("/api/agenda")
    public AgendaRespuesta agenda(@RequestAttribute(SesionClinica.CLINICA) Long clinicaId,
                                  @RequestParam Long profesionalId,
                                  @RequestParam(required = false) LocalDate fecha) {
        return agenda.agenda(clinicaId, profesionalId, fecha);
    }
}
