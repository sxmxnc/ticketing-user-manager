package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für {@link RegistrierungsDto} — die maskierte {@code toString()} darf das
 * Klartext-Passwort nicht preisgeben (Sicherheit), plus Wertsemantik des Records.
 */
class RegistrierungsDtoTest {

    @Test
    void toString_maskiertPasswort() {
        RegistrierungsDto dto = new RegistrierungsDto("neu@example.test", "GeheimesPasswort1!", "Max", "Mustermann");

        String text = dto.toString();

        assertTrue(text.contains("neu@example.test"));
        assertTrue(text.contains("***"));
        assertFalse(text.contains("GeheimesPasswort1!"));
    }

    @Test
    void accessoren_liefernWerte() {
        RegistrierungsDto dto = new RegistrierungsDto("a@b.de", "pw", "Max", "Mustermann");

        assertEquals("a@b.de", dto.email());
        assertEquals("pw", dto.password());
        assertEquals("Max", dto.firstName());
        assertEquals("Mustermann", dto.lastName());
    }

    @Test
    void equalsUndHashCode_wertsemantik() {
        RegistrierungsDto a = new RegistrierungsDto("a@b.de", "pw", "Max", "Mustermann");
        RegistrierungsDto b = new RegistrierungsDto("a@b.de", "pw", "Max", "Mustermann");
        RegistrierungsDto anders = new RegistrierungsDto("x@y.de", "pw", "Max", "Mustermann");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, anders);
    }
}
