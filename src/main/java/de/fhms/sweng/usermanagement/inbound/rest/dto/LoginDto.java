package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für die Anmeldung (Login) eines Nutzers.
 *
 * <p>Trägt die Anmeldedaten aus dem JSON-Request. Die Prüfung übernimmt der
 * {@code AuthenticationManager} (über {@code UserDetailsServiceImpl} + {@code PasswordEncoder}) —
 * nicht dieses DTO.</p>
 *
 * @param email    die E-Mail-Adresse als Login-Name
 * @param password das Klartext-Passwort (wird nur zur Prüfung verwendet, nie gespeichert)
 */
public record LoginDto(String email, String password) {

    /**
     * Überschriebene toString-Methode: maskiert das Passwort.
     * Verhindert, dass das Klartext-Passwort versehentlich in Logs landet.
     *
     * @return String-Repräsentation ohne Klartext-Passwort
     */
    @Override
    public String toString() {
        return "LoginDto[email=" + email + ", password=***]";
    }
}
