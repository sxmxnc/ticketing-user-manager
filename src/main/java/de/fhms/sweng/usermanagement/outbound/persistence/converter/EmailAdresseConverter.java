package de.fhms.sweng.usermanagement.outbound.persistence.converter;

import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA-Konverter für das Value Object {@link EmailAdresse}.
 *
 * <p>Übersetzt zwischen dem Java-Record {@code EmailAdresse} und einer {@code String}-Spalte
 * in der Datenbank. Mit {@code autoApply=true} wird dieser Konverter automatisch auf alle
 * Felder vom Typ {@code EmailAdresse} angewendet — kein {@code @Convert}-Attribut nötig.</p>
 */
@Converter(autoApply = true)
public class EmailAdresseConverter implements AttributeConverter<EmailAdresse, String> {

    /**
     * Wandelt eine {@link EmailAdresse} in einen {@code String} für die DB-Spalte um.
     *
     * @param emailAdresse der Java-seitige Wert (kann {@code null} sein)
     * @return E-Mail-Adresse als String oder {@code null}
     */
    @Override
    public String convertToDatabaseColumn(EmailAdresse emailAdresse) {
        if (emailAdresse == null) {
            return null;
        }
        return emailAdresse.adresse();
    }

    /**
     * Wandelt einen {@code String} aus der DB zurück in eine {@link EmailAdresse} um.
     *
     * @param wert der DB-seitige Wert (kann {@code null} sein)
     * @return rekonstruiertes {@link EmailAdresse}-Record oder {@code null}
     */
    @Override
    public EmailAdresse convertToEntityAttribute(String wert) {
        if (wert == null) {
            return null;
        }
        return new EmailAdresse(wert);
    }
}
