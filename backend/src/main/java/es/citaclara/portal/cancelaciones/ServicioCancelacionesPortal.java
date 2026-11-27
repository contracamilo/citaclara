package es.citaclara.portal.cancelaciones;

import es.citaclara.comun.ZonaClinica;
import es.citaclara.comun.errores.ErrorNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Aviso de cancelaciones del portal en recepción (US4, FR-019–FR-023). Solo informativo: no toca la
 * agenda, cuyo hueco ya quedó libre al cancelar (FR-015, FR-023).
 */
@Service
public class ServicioCancelacionesPortal {

    private final RepositorioCancelaciones cancelaciones;
    private final Clock reloj;

    public ServicioCancelacionesPortal(RepositorioCancelaciones cancelaciones, Clock reloj) {
        this.cancelaciones = cancelaciones;
        this.reloj = reloj;
    }

    public record CancelacionPortalRespuesta(long citaId, String paciente, String profesional, String fecha,
                                             String horaInicio, String horaFin, String servicio,
                                             String canceladaFecha, String canceladaHora, boolean huecoPasado) {
    }

    public record CancelacionesPortalRespuesta(int pendientes, List<CancelacionPortalRespuesta> cancelaciones) {
    }

    @Transactional(readOnly = true)
    public CancelacionesPortalRespuesta pendientes(long clinicaId) {
        Instant ahora = reloj.instant();
        List<CancelacionPortalRespuesta> lista = cancelaciones.pendientes(clinicaId).stream()
                .map(c -> new CancelacionPortalRespuesta(
                        c.citaId(), c.paciente(), c.profesional(),
                        ZonaClinica.fechaLocal(c.inicio()).toString(),
                        ZonaClinica.formatoHora(c.inicio()), ZonaClinica.formatoHora(c.fin()), c.servicio(),
                        ZonaClinica.fechaLocal(c.canceladaEn()).toString(), ZonaClinica.formatoHora(c.canceladaEn()),
                        !ahora.isBefore(c.inicio())))
                .toList();
        return new CancelacionesPortalRespuesta(lista.size(), lista);
    }

    /** Idempotente (FR-021); una cancelación de otra clínica o inexistente responde 404. */
    @Transactional
    public void revisar(long clinicaId, long citaId) {
        if (cancelaciones.marcarRevisada(citaId, clinicaId, reloj.instant()) == 0 && !cancelaciones.existe(citaId, clinicaId)) {
            throw ErrorNegocio.noEncontrado();
        }
    }
}
