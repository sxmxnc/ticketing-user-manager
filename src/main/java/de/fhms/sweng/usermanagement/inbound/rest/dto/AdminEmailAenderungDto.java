package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für das Ändern der E-Mail-Adresse eines fremden Kontos durch einen Administrator.
 *
 * <p>Anders als beim Self-Service ist <b>kein</b> Passwort nötig — der Administrator handelt kraft
 * seiner Rolle. Die Validierung des E-Mail-Formats übernimmt das Value Object {@code EmailAdresse}
 * im Service.</p>
 *
 * @param newEmail die gewünschte neue E-Mail-Adresse des Zielkontos
 */
public record AdminEmailAenderungDto(String newEmail) {
}
