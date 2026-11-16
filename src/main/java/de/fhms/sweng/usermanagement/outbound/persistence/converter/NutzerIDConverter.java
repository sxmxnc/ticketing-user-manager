package de.fhms.sweng.usermanagement.outbound.persistence.converter;

import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/**
 * JPA-Konverter für das Value Object {@link NutzerID}.
 *
 * <p>Übersetzt zwischen dem Java-Record {@code NutzerID} und einer {@code String}-Spalte
 * in der Datenbank (UUID als Text gespeichert). Mit {@code autoApply=true} wird dieser
 * Konverter automatisch auf alle Felder vom Typ {@code NutzerID} angewendet — kein
 * {@code @Convert}-Attribut an der Entity nötig (sauberes Hexagonal: Domain importiert kein JPA).</p>
 */
@Converter(autoApply = true)
public class NutzerIDConverter implements AttributeConverter<NutzerID, String> {

    /**
     * Wandelt eine {@link NutzerID} in einen {@code String} für die DB-Spalte um.
     *
     * @param nutzerId der Java-seitige Wert (kann {@code null} sein)
     * @return UUID als String oder {@code null}
     */
    @Override
    public String convertToDatabaseColumn(NutzerID nutzerId) {
        // null-sicher: JPA übergibt null z. B. bei noch nicht persistierten Entitäten
        if (nutzerId == null) {
            return null;
        }
        return nutzerId.wert().toString();
    }

    /**
     * Wandelt einen {@code String} aus der DB zurück in eine {@link NutzerID} um.
     *
     * @param wert der DB-seitige Wert (kann {@code null} sein)
     * @return rekonstruiertes {@link NutzerID}-Record oder {@code null}
     */
    @Override
    public NutzerID convertToEntityAttribute(String wert) {
        if (wert == null) {
            return null;
        }
        return new NutzerID(UUID.fromString(wert));
    }
}
