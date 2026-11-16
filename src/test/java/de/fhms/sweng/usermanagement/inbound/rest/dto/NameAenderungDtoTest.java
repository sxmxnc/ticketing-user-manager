package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit-Tests für {@link NameAenderungDto} — Wertsemantik des Records (Accessoren, equals/hashCode).
 */
class NameAenderungDtoTest {

    @Test
    void accessoren_liefernWerte() {
        NameAenderungDto dto = new NameAenderungDto("Max", "Mustermann");

        assertEquals("Max", dto.firstName());
        assertEquals("Mustermann", dto.lastName());
    }

    @Test
    void equalsUndHashCode_wertsemantik() {
        NameAenderungDto a = new NameAenderungDto("Max", "Mustermann");
        NameAenderungDto b = new NameAenderungDto("Max", "Mustermann");
        NameAenderungDto anders = new NameAenderungDto("Erika", "Musterfrau");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, anders);
    }
}
