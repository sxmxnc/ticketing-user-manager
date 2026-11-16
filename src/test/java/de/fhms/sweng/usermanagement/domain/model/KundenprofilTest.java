package de.fhms.sweng.usermanagement.domain.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für Kundenprofil.
 * Kein Spring-Kontext nötig — reiner Java-Test der Domänenlogik.
 */
class KundenprofilTest {

    private Adresse testAdresse() {
        return new Adresse("Hauptstraße", "12", "48149", "Münster", "Deutschland");
    }

    @Test
    void konstruktor_erstelltProfilMitAdresseUndInteressen() {
        Set<Sparte> interessen = EnumSet.of(Sparte.MUSIC, Sparte.THEATER);
        Kundenprofil profil = new Kundenprofil(testAdresse(), interessen);

        assertEquals(testAdresse(), profil.getRechnungsAdresse());
        assertTrue(profil.getInteressen().contains(Sparte.MUSIC));
        assertTrue(profil.getInteressen().contains(Sparte.THEATER));
    }

    @Test
    void konstruktor_mitNullAdresse_wirftNullPointerException() {
        // EnumSet.of() außerhalb der Lambda — nur new Kundenprofil() soll werfen
        Set<Sparte> interessen = EnumSet.of(Sparte.MUSIC);
        assertThrows(NullPointerException.class,
                () -> new Kundenprofil(null, interessen));
    }

    @Test
    void konstruktor_mitNullInteressen_erstelltLeeresSet() {
        Kundenprofil profil = new Kundenprofil(testAdresse(), null);
        assertNotNull(profil.getInteressen());
        assertTrue(profil.getInteressen().isEmpty());
    }

    @Test
    void konstruktor_mitLeeremInteressenSet_erstelltLeeresSet() {
        // Leeres (nicht-null) Set: deckt den isEmpty()-Zweig in kopiereInteressen ab —
        // EnumSet.copyOf würde bei leerer Collection sonst eine IllegalArgumentException werfen.
        Kundenprofil profil = new Kundenprofil(testAdresse(), EnumSet.noneOf(Sparte.class));
        assertNotNull(profil.getInteressen());
        assertTrue(profil.getInteressen().isEmpty());
    }

    @Test
    void aktualisieren_aendertAdresseUndInteressen() {
        Kundenprofil profil = new Kundenprofil(testAdresse(), EnumSet.of(Sparte.MUSIC));
        Adresse neueAdresse = new Adresse("Münsterstraße", "5", "48145", "Münster", "Deutschland");

        profil.aktualisieren(neueAdresse, EnumSet.of(Sparte.COMEDY));

        assertEquals(neueAdresse, profil.getRechnungsAdresse());
        assertTrue(profil.getInteressen().contains(Sparte.COMEDY));
        assertFalse(profil.getInteressen().contains(Sparte.MUSIC));
    }

    @Test
    void aktualisieren_mitNullAdresse_wirftNullPointerException() {
        // EnumSet.of() außerhalb der Lambda — nur aktualisieren() soll werfen
        Kundenprofil profil = new Kundenprofil(testAdresse(), EnumSet.of(Sparte.MUSIC));
        Set<Sparte> neueInteressen = EnumSet.of(Sparte.COMEDY);
        assertThrows(NullPointerException.class,
                () -> profil.aktualisieren(null, neueInteressen));
    }

    @Test
    void getInteressen_gibtUnveraenderlichesSetZurueck() {
        Kundenprofil profil = new Kundenprofil(testAdresse(), EnumSet.of(Sparte.MUSIC));
        // Erst holen (außerhalb Lambda), dann nur .add() in der Lambda testen
        Set<Sparte> interessen = profil.getInteressen();
        assertThrows(UnsupportedOperationException.class,
                () -> interessen.add(Sparte.COMEDY));
    }

    @Test
    void aktualisieren_mitNullInteressen_erstelltLeeresSet() {
        // Else-Branch in aktualisieren(): null-Interessen → leeres Set
        Kundenprofil profil = new Kundenprofil(testAdresse(), EnumSet.of(Sparte.MUSIC));
        profil.aktualisieren(testAdresse(), null);
        assertTrue(profil.getInteressen().isEmpty());
    }

    @Test
    void defensiverCopy_externeAenderungWirktSichNichtAus() {
        // Defensiver Copy prüfen: Set nach Übergabe verändern darf Profil nicht ändern
        Set<Sparte> interessen = EnumSet.of(Sparte.MUSIC);
        Kundenprofil profil = new Kundenprofil(testAdresse(), interessen);
        interessen.add(Sparte.COMEDY);

        assertFalse(profil.getInteressen().contains(Sparte.COMEDY));
    }
}
