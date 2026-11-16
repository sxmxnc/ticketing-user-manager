package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für {@link PasswortAenderungDto} — die maskierte {@code toString()} darf weder das
 * alte noch das neue Passwort preisgeben (Sicherheit).
 */
class PasswortAenderungDtoTest {

    @Test
    void toString_maskiertBeidePasswoerter() {
        PasswortAenderungDto dto = new PasswortAenderungDto("AltesPasswort1!", "NeuesPasswort1!");

        String text = dto.toString();

        assertTrue(text.contains("***"));
        assertFalse(text.contains("AltesPasswort1!"));
        assertFalse(text.contains("NeuesPasswort1!"));
    }

    @Test
    void accessoren_liefernWerte() {
        PasswortAenderungDto dto = new PasswortAenderungDto("alt", "neu");

        assertEquals("alt", dto.oldPassword());
        assertEquals("neu", dto.newPassword());
    }
}
