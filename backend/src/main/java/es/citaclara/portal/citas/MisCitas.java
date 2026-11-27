package es.citaclara.portal.citas;

import java.util.List;

/** Respuesta de {@code GET /api/portal/citas} (US2, FR-008). */
public record MisCitas(String clinicaNombre, List<String> pacientes, List<CitaPortal> proximas, List<CitaPortal> pasadas) {
}
