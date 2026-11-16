package de.fhms.sweng.usermanagement.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für das Value Object EmailAdresse.
 * Kein Spring-Kontext nötig — reiner Java-Record-Test.
 */
class EmailAdresseTest {

    @Test
    void konstruktor_mitGueltigerAdresse_erstelltEmailAdresse() {
        EmailAdresse email = new EmailAdresse("simon@example.test");
        assertEquals("simon@example.test", email.adresse());
    }

    @Test
    void konstruktor_mitNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAdresse(null));
    }

    @Test
    void konstruktor_ohneAt_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAdresse("keinAt.de"));
    }

    @Test
    void konstruktor_ohneDomainPunkt_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAdresse("simon@localhost"));
    }

    @Test
    void konstruktor_leereLokalteil_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new EmailAdresse("@example.test"));
    }

    @Test
    void istGueltig_mitGueltigerAdresse_gibtTrue() {
        assertTrue(EmailAdresse.istGueltig("nutzer@example.com"));
    }

    @Test
    void istGueltig_mitNull_gibtFalse() {
        assertFalse(EmailAdresse.istGueltig(null));
    }

    @Test
    void istGueltig_mitLeeremString_gibtFalse() {
        assertFalse(EmailAdresse.istGueltig(""));
    }

    @Test
    void istGueltig_domainBeginnMitPunkt_gibtFalse() {
        // startsWith-Branch: ".fh-muenster.de" ist ungültig
        assertFalse(EmailAdresse.istGueltig("simon@.fh-muenster.de"));
    }

    @Test
    void istGueltig_domainEndetMitPunkt_gibtFalse() {
        // endsWith-Branch: "fh-muenster." ist ungültig
        assertFalse(EmailAdresse.istGueltig("simon@fh-muenster."));
    }

    @Test
    void istGueltig_mehrereAtZeichen_gibtFalse() {
        // Genau ein @ erlaubt — abschließendes @ darf nicht durchrutschen (split-Falle)
        assertFalse(EmailAdresse.istGueltig("simon@example.test@"));
    }

    @Test
    void equals_gleicheAdresse_ergibtGleichheit() {
        // Value Object: zwei Instanzen mit demselben Wert müssen gleich sein
        EmailAdresse e1 = new EmailAdresse("simon@example.test");
        EmailAdresse e2 = new EmailAdresse("simon@example.test");
        assertEquals(e1, e2);
    }

    @Test
    void konstruktor_normalisiertAufKleinbuchstaben() {
        // Großschreibung in Eingabe → intern kleingeschrieben gespeichert
        EmailAdresse email = new EmailAdresse("Simon@example.test");
        assertEquals("simon@example.test", email.adresse());
    }

    @Test
    void equals_ignoriertGrossKleinschreibung() {
        // Folge der Normalisierung: gemischte Schreibweise ergibt dasselbe Value Object
        EmailAdresse gross = new EmailAdresse("Simon@example.test");
        EmailAdresse klein = new EmailAdresse("simon@example.test");
        assertEquals(klein, gross);
    }
}
