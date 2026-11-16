package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für das Zuweisen einer Rolle an ein Konto (Admin-Endpunkt).
 *
 * <p>Enthält den Rollennamen als String. Der Controller wandelt ihn in das
 * {@code Nutzerrolle}-Enum um; ein unbekannter Name führt zu HTTP 400.</p>
 *
 * @param role der Name der zuzuweisenden Rolle (z. B. "EVENT_MANAGER")
 */
public record RollenZuweisungDto(String role) {
}
