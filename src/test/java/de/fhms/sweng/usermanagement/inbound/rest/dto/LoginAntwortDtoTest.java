package de.fhms.sweng.usermanagement.inbound.rest.dto;

import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für {@link LoginAntwortDto} — die {@code von()}-Abbildung Domain→DTO und die
 * Token-Maskierung in {@code toString()} (das JWT ist ein Credential und darf nicht in Logs auftauchen).
 */
class LoginAntwortDtoTest {

    private NutzerAccount testKonto() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", new BCryptPasswordEncoder());
        return NutzerAccount.registrieren(
                new EmailAdresse("max@example.test"), hash, new PersonenName("Max", "Mustermann"));
    }

    @Test
    void von_uebernimmtTokenUndProfildaten() {
        NutzerAccount konto = testKonto();

        LoginAntwortDto dto = LoginAntwortDto.von("tok", konto);

        assertEquals("tok", dto.token());
        assertEquals(konto.getNutzerId().wert(), dto.userId());
        assertEquals("max@example.test", dto.email());
        assertEquals("Max Mustermann", dto.displayName());
        assertTrue(dto.roles().contains(Nutzerrolle.PROSPECT.name()));
    }

    @Test
    void toString_maskiertToken() {
        LoginAntwortDto dto = LoginAntwortDto.von("eyJhbGci.geheim.signatur", testKonto());

        String text = dto.toString();

        assertTrue(text.contains("***"), "Token muss maskiert sein");
        assertFalse(text.contains("eyJhbGci.geheim.signatur"), "Das echte Token darf nicht im toString stehen");
    }

    @Test
    void equalsUndHashCode_wertsemantik() {
        NutzerAccount konto = testKonto();
        LoginAntwortDto a = LoginAntwortDto.von("t", konto);
        LoginAntwortDto b = LoginAntwortDto.von("t", konto);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, LoginAntwortDto.von("u", konto));
    }
}
