package es.citaclara.portal.citas;

import es.citaclara.cita.Cita;
import es.citaclara.cita.RepositorioCita;
import es.citaclara.clinica.RepositorioClinica;
import es.citaclara.comun.ZonaClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import es.citaclara.portal.acceso.RepositorioPacientesPortal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Citas del paciente en el portal (US2, FR-008–FR-011). La vista de cada cita se calcula en el
 * servidor con el reloj y la zona de la clínica (data-model «Vista de cita en el portal»).
 */
@Service
public class ServicioMisCitas {

    private final RepositorioCita citas;
    private final RepositorioClinica clinicas;
    private final RepositorioPacientesPortal pacientes;
    private final Clock reloj;

    public ServicioMisCitas(RepositorioCita citas, RepositorioClinica clinicas, RepositorioPacientesPortal pacientes,
                            Clock reloj) {
        this.citas = citas;
        this.clinicas = clinicas;
        this.pacientes = pacientes;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public MisCitas de(long clinicaId, String email) {
        String nombreClinica = clinicas.findById(clinicaId).orElseThrow(ErrorNegocio::noEncontrado).getNombre();
        Instant ahora = reloj.instant();
        List<Cita> todas = citas.delPortal(clinicaId, email);
        List<CitaPortal> proximas = new ArrayList<>();
        List<CitaPortal> pasadas = new ArrayList<>();
        for (Cita cita : todas.stream().sorted(Comparator.comparing(Cita::getInicio).thenComparing(Cita::getId)).toList()) {
            CitaPortal vista = vista(cita, ahora);
            if (vista.estadoPortal() == EstadoPortal.RESERVADA) {
                proximas.add(vista);
            } else {
                pasadas.add(vista);
            }
        }
        return new MisCitas(nombreClinica, pacientes.nombresConEmail(clinicaId, email), proximas, pasadas.reversed());
    }

    /** Vista de una cita para el paciente (también la usa la cancelación, US3). */
    public static CitaPortal vista(Cita cita, Instant ahora) {
        EstadoPortal estado = switch (cita.getEstado()) {
            case RESERVADA -> ahora.isBefore(cita.getInicio()) ? EstadoPortal.RESERVADA : EstadoPortal.PENDIENTE_CIERRE;
            case COMPLETADA -> EstadoPortal.REALIZADA;
            case NO_ASISTIDA -> EstadoPortal.NO_ACUDISTE;
            case CANCELADA -> EstadoPortal.CANCELADA;
        };
        boolean cancelable = estado == EstadoPortal.RESERVADA
                && ReglaCancelacionPortal.dentroDePlazo(cita.getInicio(), ahora);
        String motivo = estado == EstadoPortal.RESERVADA && !cancelable ? Mensajes.FUERA_DE_PLAZO : null;
        return new CitaPortal(
                cita.getId(),
                cita.getVersion(),
                cita.getPaciente().getNombre(),
                ZonaClinica.fechaLocal(cita.getInicio()).toString(),
                ZonaClinica.formatoHora(cita.getInicio()),
                ZonaClinica.formatoHora(cita.getFin()),
                cita.getProfesional().getNombre(),
                cita.getServicio().getNombre(),
                cita.getImporteCentimos(),
                estado,
                cancelable,
                motivo);
    }
}
