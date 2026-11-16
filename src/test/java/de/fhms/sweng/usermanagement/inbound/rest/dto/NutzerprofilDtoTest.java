package de.fhms.sweng.usermanagement.inbound.rest.dto;

import de.fhms.sweng.usermanagement.domain.model.Adresse;
import de.fhms.sweng.usermanagement.domain.model.EmailAdresse;
import de.fhms.sweng.usermanagement.domain.model.Kundenprofil;
import de.fhms.sweng.usermanagement.domain.model.NutzerAccount;
import de.fhms.sweng.usermanagement.domain.model.Nutzerrolle;
import de.fhms.sweng.usermanagement.domain.model.PasswortHash;
import de.fhms.sweng.usermanagement.domain.model.PersonenName;
import de.fhms.sweng.usermanagement.domain.model.Sparte;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests für das Mapping {@link NutzerprofilDto#von(NutzerAccount)} (Domain → DTO) —
 * deckt beide Pfade ab: mit und ohne vorhandenes Kundenprofil.
 */
class NutzerprofilDtoTest {

    private NutzerAccount neuesKonto() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", new BCryptPasswordEncoder());
        return NutzerAccount.registrieren(
                new EmailAdresse("demo@example.test"), hash, new PersonenName("Max", "Mustermann"));
    }

    @Test
    void von_ohneKundenprofil_adresseNullInteressenLeer() {
        NutzerprofilDto dto = NutzerprofilDto.von(neuesKonto());

        assertEquals("demo@example.test", dto.email());
        // Frisch registriertes Konto startet PENDING (Semantic Lock)
        assertEquals("PENDING", dto.status());
        assertTrue(dto.roles().contains(Nutzerrolle.PROSPECT.name()));
        // Kein Kundenprofil → Adresse null, Interessen leer
        assertNull(dto.billingAddress());
        assertTrue(dto.interests().isEmpty());
    }

    @Test
    void von_uebernimmtNameUndAnzeigename() {
        NutzerprofilDto dto = NutzerprofilDto.von(neuesKonto());

        assertEquals("Max", dto.firstName());
        assertEquals("Mustermann", dto.lastName());
        assertEquals("Max Mustermann", dto.displayName());
    }

    @Test
    void von_mitKundenprofil_uebernimmtAdresseUndInteressen() {
        NutzerAccount konto = neuesKonto();
        Adresse adresse = new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
        konto.setKundenprofil(new Kundenprofil(adresse, EnumSet.of(Sparte.MUSIC, Sparte.THEATER)));

        NutzerprofilDto dto = NutzerprofilDto.von(konto);

        assertNotNull(dto.billingAddress());
        assertEquals("Hauptstraße", dto.billingAddress().street());
        assertTrue(dto.interests().contains(Sparte.MUSIC.name()));
        assertTrue(dto.interests().contains(Sparte.THEATER.name()));
    }
}
