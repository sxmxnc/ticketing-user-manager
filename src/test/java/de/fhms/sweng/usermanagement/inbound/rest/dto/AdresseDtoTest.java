package de.fhms.sweng.usermanagement.inbound.rest.dto;

import de.fhms.sweng.usermanagement.domain.model.Adresse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-Tests für {@link AdresseDto} — Mapping in beide Richtungen (Domain ↔ DTO) und Wertsemantik.
 */
class AdresseDtoTest {

    @Test
    void von_undZuAdresse_sindRoundtripKonsistent() {
        Adresse adresse = new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");

        AdresseDto dto = AdresseDto.von(adresse);
        assertEquals("Hauptstraße", dto.street());
        assertEquals("12", dto.houseNumber());
        assertEquals("48149", dto.zipCode());
        assertEquals("Münster", dto.city());
        assertEquals("Deutschland", dto.country());

        // DTO → Domain muss wieder das ursprüngliche Value Object ergeben
        assertEquals(adresse, dto.zuAdresse());
    }

    @Test
    void equalsUndHashCode_wertsemantik() {
        AdresseDto a = new AdresseDto("S", "1", "12345", "Stadt", "Land");
        AdresseDto b = new AdresseDto("S", "1", "12345", "Stadt", "Land");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
