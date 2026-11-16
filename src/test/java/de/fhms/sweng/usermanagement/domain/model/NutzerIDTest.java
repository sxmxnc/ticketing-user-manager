package de.fhms.sweng.usermanagement.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für das Value Object NutzerID.
 * Kein Spring-Kontext nötig — reiner Java-Record-Test.
 */
class NutzerIDTest {

    @Test
    void konstruktor_mitGueltigemUUID_erstelltNutzerID() {
        UUID uuid = UUID.randomUUID();
        NutzerID id = new NutzerID(uuid);
        assertEquals(uuid, id.wert());
    }

    @Test
    void konstruktor_mitNull_wirftNullPointerException() {
        assertThrows(NullPointerException.class, () -> new NutzerID(null));
    }

    @Test
    void neu_erstelltEindeutigeIDs() {
        // Zwei aufeinanderfolgende IDs müssen verschieden sein (UUID.randomUUID())
        NutzerID id1 = NutzerID.neu();
        NutzerID id2 = NutzerID.neu();
        assertNotEquals(id1, id2);
    }

    @Test
    void equals_gleicheUUID_ergibtGleichheit() {
        // Value Object: Gleichheit über Wert, nicht Objektidentität
        UUID uuid = UUID.randomUUID();
        NutzerID id1 = new NutzerID(uuid);
        NutzerID id2 = new NutzerID(uuid);
        assertEquals(id1, id2);
    }

    @Test
    void ausText_gueltigeUUID_erstelltNutzerID() {
        UUID uuid = UUID.randomUUID();
        NutzerID id = NutzerID.ausText(uuid.toString());
        assertEquals(uuid, id.wert());
    }

    @Test
    void ausText_ungueltigerString_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> NutzerID.ausText("keine-uuid"));
    }
}
