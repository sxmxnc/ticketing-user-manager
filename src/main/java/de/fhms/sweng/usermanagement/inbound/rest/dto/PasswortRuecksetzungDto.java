package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für das Zurücksetzen des Passworts eines fremden Kontos durch einen Administrator.
 *
 * <p>Anders als beim Self-Service ist <b>kein</b> altes Passwort nötig — der Administrator handelt
 * kraft seiner Rolle. Die Mindestlänge des neuen Passworts prüft das Value Object {@code PasswortHash}
 * im Service (→ HTTP 400 bei zu kurzem Passwort).</p>
 *
 * @param newPassword das neue Klartext-Passwort (mind. 10 Zeichen)
 */
public record PasswortRuecksetzungDto(String newPassword) {

    /**
     * Überschriebene toString-Methode: maskiert das Passwort.
     * Verhindert, dass das Klartext-Passwort versehentlich in Logs landet.
     *
     * @return String-Repräsentation ohne Klartext-Passwort
     */
    @Override
    public String toString() {
        return "PasswortRuecksetzungDto[newPassword=***]";
    }
}
