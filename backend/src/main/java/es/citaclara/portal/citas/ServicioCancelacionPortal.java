package es.citaclara.portal.citas;

import es.citaclara.cita.Cita;
import es.citaclara.cita.ComprobacionPortal;
import es.citaclara.cita.EstadoCita;
import es.citaclara.cita.ServicioEstadoCita;
import es.citaclara.comun.errores.ErrorNegocio;
import es.citaclara.comun.errores.Mensajes;
import es.citaclara.portal.acceso.EmailPortal;
import es.citaclara.portal.cancelaciones.RepositorioCancelaciones;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Cancelación de una cita por el paciente (US3, FR-012–FR-018). Orden de comprobación de
 * data-model.md: propiedad (404) → idempotencia (200) → versión y estado (409) → plazo (422).
 */
@Service
public class ServicioCancelacionPortal {

    private final ServicioEstadoCita estados;
    private final RepositorioCancelaciones cancelaciones;

    public ServicioCancelacionPortal(ServicioEstadoCita estados, RepositorioCancelaciones cancelaciones) {
        this.estados = estados;
        this.cancelaciones = cancelaciones;
    }

    public CitaPortal cancelar(long clinicaId, String email, long citaId, int versionVista) {
        return estados.cancelarDesdePortal(clinicaId, citaId, versionVista, new ComprobacionPortal<>() {

            @Override
            public Optional<CitaPortal> verificar(Cita cita, int version, Instant ahora) {
                String emailFicha = cita.getPaciente().getEmail();
                if (emailFicha == null || !EmailPortal.normalizar(emailFicha).equals(email)) {
                    throw ErrorNegocio.noEncontrado(); // ajena = inexistente (FR-028)
                }
                if (canceladaDesdePortal(cita)) {
                    return Optional.of(ServicioMisCitas.vista(cita, ahora)); // US3-AC7
                }
                if (cita.getVersion() != version || cita.getEstado() != EstadoCita.RESERVADA) {
                    throw conflicto(cita, ahora);
                }
                if (!ReglaCancelacionPortal.dentroDePlazo(cita.getInicio(), ahora)) {
                    throw new ErrorNegocio(HttpStatus.UNPROCESSABLE_CONTENT, "FUERA_DE_PLAZO", Mensajes.FUERA_DE_PLAZO);
                }
                return Optional.empty();
            }

            @Override
            public void despuesDeCancelar(Cita cita, Instant ahora) {
                cancelaciones.insertar(cita.getId(), clinicaId, ahora);
            }

            @Override
            public CitaPortal resultado(Cita cita, Instant ahora) {
                return ServicioMisCitas.vista(cita, ahora);
            }

            @Override
            public CitaPortal alPerderCarrera(Cita actual, Instant ahora) {
                if (canceladaDesdePortal(actual)) {
                    return ServicioMisCitas.vista(actual, ahora);
                }
                throw conflicto(actual, ahora);
            }

            private boolean canceladaDesdePortal(Cita cita) {
                return cita.getEstado() == EstadoCita.CANCELADA && cancelaciones.existe(cita.getId(), clinicaId);
            }
        });
    }

    private static ErrorNegocio conflicto(Cita actual, Instant ahora) {
        return new ErrorNegocio(HttpStatus.CONFLICT, "ESTADO_YA_CAMBIADO", Mensajes.CITA_PORTAL_YA_CAMBIADA)
                .con("citaActual", ServicioMisCitas.vista(actual, ahora));
    }
}
