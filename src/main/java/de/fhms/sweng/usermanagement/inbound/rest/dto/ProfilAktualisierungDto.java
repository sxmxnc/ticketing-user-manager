package de.fhms.sweng.usermanagement.inbound.rest.dto;

import java.util.Set;

/**
 * Request-DTO für das Aktualisieren des Kundenprofils (Rechnungsadresse + Interessen).
 *
 * <p>Die Interessen werden als Menge von Strings übergeben (z. B. "MUSIC", "THEATER") und
 * im Controller in das Enum {@code Sparte} umgewandelt. Ein unbekannter Wert führt zu einer
 * {@code IllegalArgumentException} (→ 400 über den ExceptionHandler).</p>
 *
 * @param billingAddress die neue Rechnungsadresse
 * @param interests      die Interessengebiete als Sparten-Namen (darf leer sein)
 */
public record ProfilAktualisierungDto(AdresseDto billingAddress, Set<String> interests) {
}
