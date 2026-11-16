package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für die Registrierung eines neuen Nutzers.
 *
 * <p>Trägt die Rohdaten aus dem JSON-Request. Die Validierung (E-Mail-Format,
 * Passwort-Mindestlänge) erfolgt nicht hier, sondern beim Umwandeln in die Value Objects
 * {@code EmailAdresse} und {@code PasswortHash} im NutzerService — deren Konstruktoren werfen
 * bei Ungültigkeit eine {@code IllegalArgumentException} (→ 400 über den ExceptionHandler).</p>
 *
 * @param email     die E-Mail-Adresse des neuen Nutzers (dient auch als Login-Name)
 * @param password  das Klartext-Passwort (wird im Service sofort gehasht, nie gespeichert)
 * @param firstName der Vorname des neuen Nutzers (Pflichtangabe)
 * @param lastName  der Nachname des neuen Nutzers (Pflichtangabe)
 */
public record RegistrierungsDto(String email, String password, String firstName, String lastName) {

    /**
     * Überschriebene toString-Methode: maskiert das Passwort.
     * Verhindert, dass das Klartext-Passwort versehentlich in Logs landet.
     *
     * @return String-Repräsentation ohne Klartext-Passwort
     */
    @Override
    public String toString() {
        return "RegistrierungsDto[email=" + email + ", password=***, firstName=" + firstName
                + ", lastName=" + lastName + "]";
    }
}
