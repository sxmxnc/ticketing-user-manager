package de.fhms.sweng.usermanagement.outbound.persistence.converter;

import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA-Konverter für das Value Object {@link PasswortHash}.
 *
 * <p>Übersetzt zwischen dem Java-Record {@code PasswortHash} und einer {@code String}-Spalte
 * in der Datenbank (BCrypt-Hash als Text). Mit {@code autoApply=true} wird dieser Konverter
 * automatisch auf alle Felder vom Typ {@code PasswortHash} angewendet.</p>
 *
 * <p>Sicherheitshinweis: Der gespeicherte Wert ist immer ein BCrypt-Hash — niemals Klartext.</p>
 */
@Converter(autoApply = true)
public class PasswortHashConverter implements AttributeConverter<PasswortHash, String> {

    /**
     * Wandelt einen {@link PasswortHash} in einen {@code String} für die DB-Spalte um.
     *
     * @param passwortHash der Java-seitige Wert (kann {@code null} sein)
     * @return BCrypt-Hash als String oder {@code null}
     */
    @Override
    public String convertToDatabaseColumn(PasswortHash passwortHash) {
        if (passwortHash == null) {
            return null;
        }
        return passwortHash.hash();
    }

    /**
     * Wandelt einen {@code String} aus der DB zurück in einen {@link PasswortHash} um.
     *
     * @param wert der DB-seitige Wert (kann {@code null} sein)
     * @return rekonstruierter {@link PasswortHash} oder {@code null}
     */
    @Override
    public PasswortHash convertToEntityAttribute(String wert) {
        if (wert == null) {
            return null;
        }
        // Konstruktor von PasswortHash akzeptiert den bereits gehashten String direkt
        return new PasswortHash(wert);
    }
}
