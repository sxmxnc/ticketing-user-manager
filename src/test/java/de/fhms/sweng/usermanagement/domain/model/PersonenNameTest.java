package de.fhms.sweng.usermanagement.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für das Value Object PersonenName.
 * Kein Spring-Kontext nötig — reiner Java-Record-Test.
 */
class PersonenNameTest {

    @Test
    void konstruktor_mitGueltigenWerten_erstelltNamen() {
        PersonenName name = new PersonenName("Max", "Mustermann");
        assertEquals("Max", name.vorname());
        assertEquals("Mustermann", name.nachname());
    }

    @Test
    void konstruktor_trimmtRandleerzeichen() {
        // Normalisierung: führende/abschließende Leerzeichen werden entfernt
        PersonenName name = new PersonenName("  Max  ", "  Mustermann  ");
        assertEquals("Max", name.vorname());
        assertEquals("Mustermann", name.nachname());
    }

    @Test
    void konstruktor_vornameNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersonenName(null, "Mustermann"));
    }

    @Test
    void konstruktor_nachnameNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersonenName("Max", null));
    }

    @Test
    void konstruktor_vornameLeer_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersonenName("  ", "Mustermann"));
    }

    @Test
    void konstruktor_nachnameLeer_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersonenName("Max", "  "));
    }

    @Test
    void anzeigename_setztVornameUndNachnameZusammen() {
        PersonenName name = new PersonenName("Max", "Mustermann");
        assertEquals("Max Mustermann", name.anzeigename());
    }

    @Test
    void anzeigename_nachTrimmen_ohneRandleerzeichen() {
        // Sicherstellen, dass das Trimmen vor der Zusammensetzung greift
        PersonenName name = new PersonenName("  Max  ", "  Mustermann  ");
        assertEquals("Max Mustermann", name.anzeigename());
    }

    @Test
    void equals_gleicheFelder_ergibtGleichheit() {
        // Value Object: zwei Instanzen mit denselben Werten müssen gleich sein
        PersonenName n1 = new PersonenName("Max", "Mustermann");
        PersonenName n2 = new PersonenName("Max", "Mustermann");
        assertEquals(n1, n2);
    }
}
