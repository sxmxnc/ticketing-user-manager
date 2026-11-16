package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für {@link EmailAenderungDto} — die maskierte {@code toString()} darf das aktuelle
 * Passwort nicht preisgeben, die neue E-Mail aber schon (kein Geheimnis).
 */
class EmailAenderungDtoTest {

    @Test
    void toString_maskiertPasswort_zeigtEmail() {
        EmailAenderungDto dto = new EmailAenderungDto("neu@example.test", "GeheimesPasswort1!");

        String text = dto.toString();

        assertTrue(text.contains("neu@example.test"));
        assertTrue(text.contains("***"));
        assertFalse(text.contains("GeheimesPasswort1!"));
    }

    @Test
    void accessoren_liefernWerte() {
        EmailAenderungDto dto = new EmailAenderungDto("a@b.de", "pw");

        assertEquals("a@b.de", dto.newEmail());
        assertEquals("pw", dto.currentPassword());
    }
}
