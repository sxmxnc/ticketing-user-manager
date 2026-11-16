package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für {@link LoginDto} — Schwerpunkt: die maskierte {@code toString()} darf das
 * Klartext-Passwort niemals preisgeben (Sicherheit), plus Wertsemantik des Records.
 */
class LoginDtoTest {

    @Test
    void toString_maskiertPasswort() {
        LoginDto dto = new LoginDto("demo@example.test", "GeheimesPasswort1!");

        String text = dto.toString();

        assertTrue(text.contains("demo@example.test"), "E-Mail darf sichtbar sein");
        assertTrue(text.contains("***"), "Passwort muss maskiert sein");
        assertFalse(text.contains("GeheimesPasswort1!"), "Klartext-Passwort darf nicht im toString stehen");
    }

    @Test
    void accessoren_liefernWerte() {
        LoginDto dto = new LoginDto("a@b.de", "pw");

        assertEquals("a@b.de", dto.email());
        assertEquals("pw", dto.password());
    }

    @Test
    void equalsUndHashCode_wertsemantik() {
        LoginDto a = new LoginDto("a@b.de", "pw");
        LoginDto b = new LoginDto("a@b.de", "pw");
        LoginDto anders = new LoginDto("x@y.de", "pw");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, anders);
    }
}
