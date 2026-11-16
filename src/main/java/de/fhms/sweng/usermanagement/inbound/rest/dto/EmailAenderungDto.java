package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für das Ändern der eigenen E-Mail-Adresse (Self-Service).
 *
 * <p>Die E-Mail-Änderung ist eine sensible Operation (Login-Identität), daher muss das aktuelle
 * Passwort zur Bestätigung mitgeschickt werden. Die Validierung des E-Mail-Formats übernimmt das
 * Value Object {@code EmailAdresse} im Service.</p>
 *
 * @param newEmail        die gewünschte neue E-Mail-Adresse
 * @param currentPassword das aktuelle Klartext-Passwort zur Bestätigung
 */
public record EmailAenderungDto(String newEmail, String currentPassword) {

    /**
     * Überschriebene toString-Methode: maskiert das Passwort.
     * Verhindert, dass das Klartext-Passwort versehentlich in Logs landet.
     *
     * @return String-Repräsentation ohne Klartext-Passwort
     */
    @Override
    public String toString() {
        return "EmailAenderungDto[newEmail=" + newEmail + ", currentPassword=***]";
    }
}
