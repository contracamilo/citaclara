package es.citaclara.portal.citas;

/** Estado de una cita tal como la ve el paciente (data-model «Vista de cita en el portal», FR-009). */
public enum EstadoPortal {
    RESERVADA,
    PENDIENTE_CIERRE,
    REALIZADA,
    NO_ACUDISTE,
    CANCELADA
}
