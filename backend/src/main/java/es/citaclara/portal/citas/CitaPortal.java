package es.citaclara.portal.citas;

/**
 * Cita vista por el paciente (contracts/api.yaml «CitaPortal»). Fecha y horas locales de Madrid
 * calculadas en el servidor; sin teléfonos ni identificadores de otras fichas (FR-011).
 */
public record CitaPortal(
        long id,
        int version,
        String paciente,
        String fecha,
        String horaInicio,
        String horaFin,
        String profesional,
        String servicio,
        int importeCentimos,
        EstadoPortal estadoPortal,
        boolean cancelable,
        String motivoNoCancelable) {
}
