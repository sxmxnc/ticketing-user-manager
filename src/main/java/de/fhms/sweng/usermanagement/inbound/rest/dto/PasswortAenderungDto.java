package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für das Ändern des eigenen Passworts (Self-Service).
 *
 * <p>Das alte Passwort muss zur Bestätigung stimmen; die Mindestlänge des neuen Passworts prüft das
 * Value Object {@code PasswortHash} im Service (→ HTTP 400 bei zu kurzem Passwort).</p>
 *
 * @param oldPassword das aktuelle Klartext-Passwort zur Bestätigung
 * @param newPassword das neue Klartext-Passwort (mind. 10 Zeichen)
 */
public record PasswortAenderungDto(String oldPassword, String newPassword) {

    /**
     * Überschriebene toString-Methode: maskiert beide Passwörter.
     * Verhindert, dass Klartext-Passwörter versehentlich in Logs landen.
     *
     * @return String-Repräsentation ohne Klartext-Passwörter
     */
    @Override
    public String toString() {
        return "PasswortAenderungDto[oldPassword=***, newPassword=***]";
    }
}
