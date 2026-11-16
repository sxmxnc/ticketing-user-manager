package de.fhms.sweng.usermanagement.inbound.rest.dto;

/**
 * Request-DTO für das Ändern des eigenen Namens ({@code PUT /rest/users/me/name}).
 *
 * <p>Trägt die Rohdaten aus dem JSON-Request. Die Validierung (nicht leer) erfolgt nicht hier,
 * sondern beim Umwandeln in das Value Object {@code PersonenName} im NutzerService — dessen
 * Konstruktor wirft bei Ungültigkeit eine {@code IllegalArgumentException} (→ 400 über den
 * ExceptionHandler). Anders als bei E-Mail-/Passwortänderung ist kein aktuelles Passwort nötig,
 * da der Name nicht sicherheitskritisch ist.</p>
 *
 * @param firstName der neue Vorname
 * @param lastName  der neue Nachname
 */
public record NameAenderungDto(String firstName, String lastName) {
}
