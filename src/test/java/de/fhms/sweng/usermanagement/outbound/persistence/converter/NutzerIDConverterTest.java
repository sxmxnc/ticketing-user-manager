package de.fhms.sweng.usermanagement.outbound.persistence.converter;

import de.fhms.sweng.usermanagement.domain.model.NutzerID;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für {@link NutzerIDConverter}.
 * Kein Spring-Kontext nötig — reiner Konverter-Test (hin + zurück + null).
 */
class NutzerIDConverterTest {

    private final NutzerIDConverter konverter = new NutzerIDConverter();

    @Test
    void convertToDatabaseColumn_wandeltNutzerIDZuString() {
        UUID uuid = UUID.randomUUID();
        NutzerID nutzerId = new NutzerID(uuid);

        String ergebnis = konverter.convertToDatabaseColumn(nutzerId);

        assertEquals(uuid.toString(), ergebnis);
    }

    @Test
    void convertToDatabaseColumn_mitNull_gibtNull() {
        String ergebnis = konverter.convertToDatabaseColumn(null);

        assertNull(ergebnis);
    }

    @Test
    void convertToEntityAttribute_wandeltStringZuNutzerID() {
        UUID uuid = UUID.randomUUID();

        NutzerID ergebnis = konverter.convertToEntityAttribute(uuid.toString());

        assertEquals(new NutzerID(uuid), ergebnis);
    }

    @Test
    void convertToEntityAttribute_mitNull_gibtNull() {
        NutzerID ergebnis = konverter.convertToEntityAttribute(null);

        assertNull(ergebnis);
    }

    @Test
    void roundTrip_hinUndZurueck_ergibtGleichenWert() {
        // Vollständiger Round-Trip: NutzerID → String → NutzerID
        NutzerID original = NutzerID.neu();

        String dbWert = konverter.convertToDatabaseColumn(original);
        NutzerID zurueck = konverter.convertToEntityAttribute(dbWert);

        assertEquals(original, zurueck);
    }
}
