package de.fhms.sweng.usermanagement.outbound.persistence.converter;

import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für {@link PasswortHashConverter}.
 * Kein Spring-Kontext nötig — reiner Konverter-Test (hin + zurück + null).
 */
class PasswortHashConverterTest {

    private final PasswortHashConverter konverter = new PasswortHashConverter();

    // BCrypt-Hash für Testzwecke: fest kodierter Hash (kein Encoder nötig im Konverter-Test)
    private static final String BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012345";

    @Test
    void convertToDatabaseColumn_wandeltPasswortHashZuString() {
        PasswortHash hash = new PasswortHash(BCRYPT_HASH);

        String ergebnis = konverter.convertToDatabaseColumn(hash);

        assertEquals(BCRYPT_HASH, ergebnis);
    }

    @Test
    void convertToDatabaseColumn_mitNull_gibtNull() {
        String ergebnis = konverter.convertToDatabaseColumn(null);

        assertNull(ergebnis);
    }

    @Test
    void convertToEntityAttribute_wandeltStringZuPasswortHash() {
        PasswortHash ergebnis = konverter.convertToEntityAttribute(BCRYPT_HASH);

        assertEquals(new PasswortHash(BCRYPT_HASH), ergebnis);
    }

    @Test
    void convertToEntityAttribute_mitNull_gibtNull() {
        PasswortHash ergebnis = konverter.convertToEntityAttribute(null);

        assertNull(ergebnis);
    }

    @Test
    void roundTrip_hinUndZurueck_ergibtGleichenWert() {
        // Vollständiger Round-Trip: PasswortHash → String → PasswortHash
        PasswortHash original = new PasswortHash(BCRYPT_HASH);

        String dbWert = konverter.convertToDatabaseColumn(original);
        PasswortHash zurueck = konverter.convertToEntityAttribute(dbWert);

        assertEquals(original, zurueck);
    }
}
