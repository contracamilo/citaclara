package es.citaclara.correo;

import java.time.Instant;

/**
 * Correo listo para enviar (contracts/correo.md). {@code nombreFichero} solo lo usa el modo simulado
 * y debe ser determinista para que un reintento reescriba el mismo fichero (research D3).
 */
public record CorreoSaliente(String destinatario, String nombreDestinatario, String asunto, String cuerpo,
                             String nombreFichero, Instant fecha) {
}
