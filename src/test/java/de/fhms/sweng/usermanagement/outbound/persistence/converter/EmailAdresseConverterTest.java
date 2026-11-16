package de.fhms.sweng.usermanagement.outbound.persistence.converter;

import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für {@link EmailAdresseConverter}.
 * Kein Spring-Kontext nötig — reiner Konverter-Test (hin + zurück + null).
 */
class EmailAdresseConverterTest {

    private final EmailAdresseConverter konverter = new EmailAdresseConverter();

    @Test
    void convertToDatabaseColumn_wandeltEmailAdresseZuString() {
        EmailAdresse email = new EmailAdresse("simon@example.test");

        String ergebnis = konverter.convertToDatabaseColumn(email);

        assertEquals("simon@example.test", ergebnis);
    }

    @Test
    void convertToDatabaseColumn_mitNull_gibtNull() {
        String ergebnis = konverter.convertToDatabaseColumn(null);

        assertNull(ergebnis);
    }

    @Test
    void convertToEntityAttribute_wandeltStringZuEmailAdresse() {
        EmailAdresse ergebnis = konverter.convertToEntityAttribute("simon@example.test");

        assertEquals(new EmailAdresse("simon@example.test"), ergebnis);
    }

    @Test
    void convertToEntityAttribute_mitNull_gibtNull() {
        EmailAdresse ergebnis = konverter.convertToEntityAttribute(null);

        assertNull(ergebnis);
    }

    @Test
    void roundTrip_hinUndZurueck_ergibtGleichenWert() {
        // Vollständiger Round-Trip: EmailAdresse → String → EmailAdresse
        EmailAdresse original = new EmailAdresse("test@example.com");

        String dbWert = konverter.convertToDatabaseColumn(original);
        EmailAdresse zurueck = konverter.convertToEntityAttribute(dbWert);

        assertEquals(original, zurueck);
    }
}
