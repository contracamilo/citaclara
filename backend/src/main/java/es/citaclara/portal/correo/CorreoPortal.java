package es.citaclara.portal.correo;

/**
 * Puerto de envío del enlace de acceso al portal (FR-001, FR-007, research R2). En producción lo
 * implementa {@link CorreoPortalSmtp}; en las pruebas, un buzón en memoria.
 */
public interface CorreoPortal {

    void enviarEnlace(String email, String nombreClinica, String url);
}
