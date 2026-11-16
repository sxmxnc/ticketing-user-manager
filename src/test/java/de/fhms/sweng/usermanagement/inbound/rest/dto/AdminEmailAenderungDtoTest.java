package de.fhms.sweng.usermanagement.inbound.rest.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit-Tests für {@link AdminEmailAenderungDto} — einfaches Request-Record (Wertsemantik).
 */
class AdminEmailAenderungDtoTest {

    @Test
    void accessor_liefertWert() {
        AdminEmailAenderungDto dto = new AdminEmailAenderungDto("neu@example.test");

        assertEquals("neu@example.test", dto.newEmail());
    }

    @Test
    void equalsUndHashCode_wertsemantik() {
        AdminEmailAenderungDto a = new AdminEmailAenderungDto("a@b.de");
        AdminEmailAenderungDto b = new AdminEmailAenderungDto("a@b.de");
        AdminEmailAenderungDto anders = new AdminEmailAenderungDto("x@y.de");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, anders);
    }
}
