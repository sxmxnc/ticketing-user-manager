package de.fhms.sweng.usermanagement.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für das Value Object PasswortHash.
 * Kein Spring-Kontext nötig — BCryptPasswordEncoder wird direkt instanziiert.
 */
class PasswortHashTest {

    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        // BCryptPasswordEncoder direkt — kein Spring-Kontext nötig
        encoder = new BCryptPasswordEncoder();
    }

    @Test
    void vonKlartext_mitGueltigemPasswort_erstelltHash() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        assertNotNull(hash);
        assertNotNull(hash.hash());
    }

    @Test
    void vonKlartext_mitZuKurzem_wirftIllegalArgumentException() {
        // 9 Zeichen — eine weniger als die Mindestlänge von 10
        assertThrows(IllegalArgumentException.class,
                () -> PasswortHash.vonKlartext("Kurz123!", encoder));
    }

    @Test
    void vonKlartext_mitNull_wirftIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswortHash.vonKlartext(null, encoder));
    }

    @Test
    void stimmt_mitKorrektemKlartext_gibtTrue() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        assertTrue(hash.stimmt("GeheimesPasswort1!", encoder));
    }

    @Test
    void stimmt_mitFalchemKlartext_gibtFalse() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        assertFalse(hash.stimmt("FalschesPasswort99!", encoder));
    }

    @Test
    void stimmt_mitNull_gibtFalse() {
        PasswortHash hash = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        assertFalse(hash.stimmt(null, encoder));
    }

    @Test
    void vonKlartext_gleichesPasswortErgibtVerschiedeneHashes() {
        // BCrypt salzt automatisch — Hashes desselben Passworts dürfen nie identisch sein
        PasswortHash hash1 = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        PasswortHash hash2 = PasswortHash.vonKlartext("GeheimesPasswort1!", encoder);
        assertNotEquals(hash1.hash(), hash2.hash());
    }

    @Test
    void konstruktor_mitNull_wirftNullPointerException() {
        // Der direkte Konstruktor (für bereits gehashte Werte aus DB) lehnt null ab
        assertThrows(NullPointerException.class, () -> new PasswortHash(null));
    }
}
