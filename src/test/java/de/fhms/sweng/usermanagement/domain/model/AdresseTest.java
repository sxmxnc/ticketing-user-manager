package de.fhms.sweng.usermanagement.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für das Value Object Adresse.
 * Kein Spring-Kontext nötig — reiner Java-Record-Test.
 */
class AdresseTest {

    @Test
    void konstruktor_mitAllenGueltigeneWerten_erstelltAdresse() {
        Adresse adresse = new Adresse("Hauptstraße", "12a", "48149", "Münster", "Deutschland");
        assertEquals("Hauptstraße", adresse.strasse());
        assertEquals("Münster", adresse.stadt());
    }

    @Test
    void konstruktor_strasseNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse(null, "12", "48149", "Münster", "Deutschland"));
    }

    @Test
    void konstruktor_hausnummerNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse("Hauptstraße", null, "48149", "Münster", "Deutschland"));
    }

    @Test
    void konstruktor_plzNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse("Hauptstraße", "12", null, "Münster", "Deutschland"));
    }

    @Test
    void konstruktor_stadtNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse("Hauptstraße", "12", "48149", null, "Deutschland"));
    }

    @Test
    void konstruktor_landNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse("Hauptstraße", "12", "48149", "Münster", null));
    }

    @Test
    void konstruktor_leererFeld_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse("  ", "12", "48149", "Münster", "Deutschland"));
    }

    @Test
    void konstruktor_leeresHausnummer_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse("Hauptstraße", "  ", "48149", "Münster", "Deutschland"));
    }

    @Test
    void konstruktor_leereStadt_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new Adresse("Hauptstraße", "12", "48149", "  ", "Deutschland"));
    }

    @Test
    void equals_gleicheFelder_ergibtGleichheit() {
        // Value Object: zwei Instanzen mit denselben Werten müssen gleich sein
        Adresse a1 = new Adresse("Hauptstraße", "12a", "48149", "Münster", "Deutschland");
        Adresse a2 = new Adresse("Hauptstraße", "12a", "48149", "Münster", "Deutschland");
        assertEquals(a1, a2);
    }
}
