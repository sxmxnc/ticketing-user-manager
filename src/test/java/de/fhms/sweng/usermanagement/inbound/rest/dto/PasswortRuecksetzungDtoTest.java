package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für {@link PasswortRuecksetzungDto} — die maskierte {@code toString()} darf das
 * neue Passwort nicht preisgeben (Sicherheit).
 */
class PasswortRuecksetzungDtoTest {

    @Test
    void toString_maskiertPasswort() {
        PasswortRuecksetzungDto dto = new PasswortRuecksetzungDto("AdminReset1!");

        String text = dto.toString();

        assertTrue(text.contains("***"));
        assertFalse(text.contains("AdminReset1!"));
    }

    @Test
    void accessor_liefertWert() {
        PasswortRuecksetzungDto dto = new PasswortRuecksetzungDto("pw");

        assertEquals("pw", dto.newPassword());
    }
}
